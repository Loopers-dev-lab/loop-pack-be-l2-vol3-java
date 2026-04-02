import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import exec from 'k6/execution';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SCALE = parseInt(__ENV.SCALE || '500');

const enterDuration = new Trend('enter_duration', true);
const pollingDuration = new Trend('polling_duration', true);
const productDuration = new Trend('product_duration', true);
const pollingErrors = new Rate('polling_errors');

export const options = {
    scenarios: {
        // 1. 대기열 진입 warmup (첫 60초)
        enter: {
            executor: 'shared-iterations',
            vus: Math.min(SCALE, 100),
            iterations: SCALE,
            maxDuration: '60s',
            exec: 'enterQueue',
        },
        // 2. Polling (진입 완료 후 시작)
        polling: {
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: SCALE },
                { duration: '2m',  target: SCALE },
                { duration: '15s', target: 0 },
            ],
            startTime: '65s',
            exec: 'pollPosition',
        },
        // 3. 혼합 부하
        products: {
            executor: 'constant-vus',
            vus: 50,
            duration: '3m30s',
            exec: 'productList',
        },
    },
    thresholds: {
        'polling_duration': ['p(95)<100'],
        'product_duration': ['p(95)<300'],
        'polling_errors': ['rate<0.01'],
    },
};

// enter: iteration 기반 유저 매핑 (1~SCALE 유니크)
export function enterQueue() {
    const userIdx = exec.scenario.iterationInTest + 1;
    const headers = {
        'X-Loopers-LoginId': `k6user${userIdx}`,
        'X-Loopers-LoginPw': 'Test1234!',
    };
    const res = http.post(`${BASE}/api/v1/queue/enter`, null, { headers });
    enterDuration.add(res.timings.duration);
    check(res, { 'enter: 2xx': (r) => r.status >= 200 && r.status < 300 });
}

// polling: VU 기반 유저 매핑 + 서버 주도 Poll 간격 적용
export function pollPosition() {
    const headers = {
        'X-Loopers-LoginId': `k6user${__VU}`,
        'X-Loopers-LoginPw': 'Test1234!',
    };
    const res = http.get(`${BASE}/api/v1/queue/position`, { headers });
    pollingDuration.add(res.timings.duration);
    pollingErrors.add(res.status >= 400);
    check(res, { 'polling: 200': (r) => r.status === 200 });

    // 서버 권장 간격 사용 (기본 2초)
    let interval = 2;
    if (res.status === 200) {
        try {
            const body = JSON.parse(res.body);
            const suggested = body.data && body.data.suggestedPollIntervalMs;
            if (suggested && suggested > 0) {
                interval = suggested / 1000;
            }
        } catch (e) { /* 파싱 실패 시 기본 2초 */ }
    }
    sleep(interval);
}

export function productList() {
    const res = http.get(`${BASE}/api/v1/products?page=0&size=20`);
    productDuration.add(res.timings.duration);
    check(res, { 'products: 200': (r) => r.status === 200 });
    sleep(1);
}
