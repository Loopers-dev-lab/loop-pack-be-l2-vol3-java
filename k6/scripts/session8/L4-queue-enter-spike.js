/**
 * Level 4 — 스파이크 진입 (Stress)
 * 목적: 블랙프라이데이 시작 직후 순간 폭주 시뮬레이션.
 * 실행: k6 run -e SPIKE=1000 k6/scripts/session8/L4-queue-enter-spike.js
 */
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { enterQueue, getProducts } from './helpers.js';
import exec from 'k6/execution';

const SPIKE_USERS = parseInt(__ENV.SPIKE || '1000');

const enterDuration = new Trend('enter_p', true);
const productDuration = new Trend('product_p', true);
const enterErrors = new Rate('enter_errors');

export const options = {
    scenarios: {
        spike: {
            executor: 'shared-iterations',
            vus: Math.min(SPIKE_USERS, 500),
            iterations: SPIKE_USERS,
            maxDuration: '60s',
            exec: 'enterSpike_fn',
        },
        background: {
            executor: 'constant-vus',
            vus: 30,
            duration: '1m30s',
            exec: 'productList_fn',
        },
    },
    thresholds: {
        'enter_p': ['p(95)<500', 'p(99)<1000'],
        'enter_errors': ['rate<0.01'],
        'product_p': ['p(95)<300'],
    },
};

export function enterSpike_fn() {
    const userIdx = exec.scenario.iterationInTest + 1;
    const res = enterQueue(userIdx);
    enterDuration.add(res.timings.duration);
    enterErrors.add(res.status >= 400);
    check(res, { 'enter: 2xx': (r) => r.status >= 200 && r.status < 300 });
}

export function productList_fn() {
    const res = getProducts();
    productDuration.add(res.timings.duration);
    check(res, { 'product: 200': (r) => r.status === 200 });
    sleep(1);
}
