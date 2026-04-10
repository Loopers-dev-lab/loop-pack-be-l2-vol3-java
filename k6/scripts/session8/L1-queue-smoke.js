/**
 * Level 1 — Smoke Test (10명)
 * 목적: 각 API가 정상 동작하는지 기능 검증. 1분 이내 완료.
 * 실행: k6 run k6/scripts/session8/L1-queue-smoke.js
 */
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { enterQueue, pollPosition, getProducts } from './helpers.js';

const enterDuration = new Trend('enter_duration', true);
const pollDuration = new Trend('poll_duration', true);
const productDuration = new Trend('product_duration', true);
const errors = new Rate('errors');

export const options = {
    scenarios: {
        smoke: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 1,
            maxDuration: '30s',
            exec: 'smokeTest',
        },
    },
    thresholds: {
        'errors': ['rate==0'],
        'enter_duration': ['p(95)<1000'],
        'poll_duration': ['p(95)<500'],
    },
};

export function smokeTest() {
    // 1. 대기열 진입
    const enterRes = enterQueue(__VU);
    enterDuration.add(enterRes.timings.duration);
    check(enterRes, {
        'enter: 2xx': (r) => r.status >= 200 && r.status < 300,
        'enter: has position': (r) => {
            try { return JSON.parse(r.body).data.position >= 0; } catch(e) { return false; }
        },
    }) || errors.add(1);

    sleep(1);

    // 2. 순번 조회
    const pollRes = pollPosition(__VU);
    pollDuration.add(pollRes.timings.duration);
    check(pollRes, {
        'poll: 200': (r) => r.status === 200,
        'poll: has status': (r) => {
            try {
                return ['WAITING', 'READY', 'NOT_IN_QUEUE'].includes(JSON.parse(r.body).data.status);
            } catch(e) { return false; }
        },
    }) || errors.add(1);

    // 3. 상품 조회
    const prodRes = getProducts();
    productDuration.add(prodRes.timings.duration);
    check(prodRes, { 'product: 200': (r) => r.status === 200 }) || errors.add(1);
}
