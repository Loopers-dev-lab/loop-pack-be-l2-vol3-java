/**
 * Level 2 — 10K Polling (with Auth Cache Warmup)
 *
 * 3-Phase 테스트:
 *   Phase 1 (0~90s):  Auth Cache Warmup — 10K 유저 인증 캐시 적재
 *   Phase 2 (95~155s): Queue Enter — 10K 유저 대기열 진입
 *   Phase 3 (160~325s): Polling Load — 10K VU 동시 폴링
 *
 * 실행:
 *   bash k6/scripts/session8/reset-queue.sh
 *   k6 run k6/scripts/session8/L2-10k-warmup.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SCALE = parseInt(__ENV.SCALE || '10000');
const WARMUP_PARALLEL = Math.min(parseInt(__ENV.WARMUP_VUS || '200'), SCALE);

const warmupDuration = new Trend('warmup_duration', true);
const enterDuration = new Trend('enter_duration', true);
const pollDuration = new Trend('polling_p', true);
const productDuration = new Trend('product_p', true);
const pollErrors = new Rate('polling_errors');
const pollCount = new Counter('polling_count');
const warmupErrors = new Rate('warmup_errors');

export const options = {
    scenarios: {
        // Phase 1: Auth Cache Warmup — position 호출로 인증 캐시 적재
        warmup: {
            executor: 'shared-iterations',
            vus: WARMUP_PARALLEL,
            iterations: SCALE,
            maxDuration: '90s',
            exec: 'warmupAuth',
        },
        // Phase 2: Queue Enter — 대기열 진입
        enter: {
            executor: 'shared-iterations',
            vus: Math.min(SCALE, 200),
            iterations: SCALE,
            maxDuration: '60s',
            startTime: '95s',
            exec: 'enterQueue',
        },
        // Phase 3: Polling Load — 10K 동시 폴링
        polling: {
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: SCALE },
                { duration: '2m', target: SCALE },
                { duration: '15s', target: 0 },
            ],
            startTime: '160s',
            exec: 'pollPosition',
        },
        // Background: 상품 목록 조회 혼합 부하
        background_products: {
            executor: 'constant-vus',
            vus: 30,
            duration: '5m30s',
            exec: 'productList',
        },
    },
    setupTimeout: '30s',
    thresholds: {
        'warmup_errors': ['rate<0.05'],
        'polling_p': ['p(95)<200', 'p(99)<500'],
        'product_p': ['p(95)<300'],
        'polling_errors': ['rate<0.01'],
    },
};

function authHeaders(userIdx) {
    return {
        'X-Loopers-LoginId': `k6user${userIdx}`,
        'X-Loopers-LoginPw': 'Test1234!',
        'Content-Type': 'application/json',
    };
}

// Phase 1: Auth Cache Warmup
// position 엔드포인트를 호출하여 인증 캐시를 미리 적재한다.
// NOT_IN_QUEUE 응답이 오지만, 인증 과정에서 캐시가 채워진다.
export function warmupAuth() {
    const userIdx = exec.scenario.iterationInTest + 1;
    const res = http.get(`${BASE}/api/v1/queue/position`, {
        headers: authHeaders(userIdx),
    });
    warmupDuration.add(res.timings.duration);
    warmupErrors.add(res.status >= 500);
    check(res, { 'warmup: 2xx': (r) => r.status >= 200 && r.status < 300 });
}

// Phase 2: Queue Enter
export function enterQueue() {
    const userIdx = exec.scenario.iterationInTest + 1;
    const res = http.post(`${BASE}/api/v1/queue/enter`, null, {
        headers: authHeaders(userIdx),
    });
    enterDuration.add(res.timings.duration);
    check(res, { 'enter: 2xx': (r) => r.status >= 200 && r.status < 300 });
}

// Phase 3: Polling
export function pollPosition() {
    const userIdx = ((__VU - 1) % SCALE) + 1;
    const res = http.get(`${BASE}/api/v1/queue/position`, {
        headers: authHeaders(userIdx),
    });
    pollDuration.add(res.timings.duration);
    pollErrors.add(res.status >= 400);
    pollCount.add(1);
    check(res, { 'poll: 200': (r) => r.status === 200 });

    // 서버 권장 폴링 간격 반영 (기본 2초)
    let interval = 2;
    if (res.status === 200) {
        try {
            const body = JSON.parse(res.body);
            const suggested = body.data && body.data.suggestedPollIntervalMs;
            if (suggested && suggested > 0) {
                interval = suggested / 1000;
            }
        } catch (e) {}
    }
    sleep(interval);
}

// Background: Product List
export function productList() {
    const res = http.get(`${BASE}/api/v1/products?page=0&size=20`);
    productDuration.add(res.timings.duration);
    check(res, { 'product: 200': (r) => r.status === 200 });
    sleep(1);
}
