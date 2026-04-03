/**
 * Level 2 — 단일 API 스케일업 (Polling)
 * 목적: Polling 단독 API의 한계점 탐색. 100 → 500 → 1K → 2K → 5K → 10K
 * 실행: k6 run -e SCALE=100 k6/scripts/session8/L2-queue-polling-scale.js
 */
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { authHeaders, pollPosition, getProducts, BASE } from './helpers.js';
import http from 'k6/http';

const SCALE = parseInt(__ENV.SCALE || '100');
const RAMP_UP = __ENV.RAMP_UP || '30s';
const DURATION = __ENV.DURATION || '2m';

const pollDuration = new Trend('polling_p', true);
const productDuration = new Trend('product_p', true);
const pollErrors = new Rate('polling_errors');
const pollCount = new Counter('polling_count');

export const options = {
    scenarios: {
        polling: {
            executor: 'ramping-vus',
            stages: [
                { duration: RAMP_UP, target: SCALE },
                { duration: DURATION, target: SCALE },
                { duration: '15s', target: 0 },
            ],
            exec: 'pollPosition_fn',
        },
        background_products: {
            executor: 'constant-vus',
            vus: 30,
            duration: '3m',
            exec: 'productList_fn',
        },
    },
    setupTimeout: '5m',
    thresholds: {
        'polling_p': ['p(95)<100', 'p(99)<200'],
        'product_p': ['p(95)<200'],
        'polling_errors': ['rate<0.01'],
    },
};

export function setup() {
    console.log(`[setup] ${SCALE}명 대기열 진입 시작...`);
    for (let i = 1; i <= SCALE; i++) {
        const headers = authHeaders(i);
        http.post(`${BASE}/api/v1/queue/enter`, null, { headers });
    }
    console.log(`[setup] ${SCALE}명 진입 완료.`);
}

export function pollPosition_fn() {
    const vu = ((__VU - 1) % SCALE) + 1;
    const res = pollPosition(vu);
    pollDuration.add(res.timings.duration);
    pollErrors.add(res.status >= 400);
    pollCount.add(1);
    check(res, { 'poll: 200': (r) => r.status === 200 });

    // 서버 권장 간격 반영
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

export function productList_fn() {
    const res = getProducts();
    productDuration.add(res.timings.duration);
    check(res, { 'product: 200': (r) => r.status === 200 });
    sleep(1);
}
