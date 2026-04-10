/**
 * Level 2 — 10K Polling (setup 우회)
 * Redis에 직접 ZADD로 10K 유저 진입 후 실행.
 * 실행 전: bash seed-queue-10k.sh
 */
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { pollPosition, getProducts } from './helpers.js';

const SCALE = parseInt(__ENV.SCALE || '10000');

const pollDuration = new Trend('polling_p', true);
const productDuration = new Trend('product_p', true);
const pollErrors = new Rate('polling_errors');
const pollCount = new Counter('polling_count');

export const options = {
    scenarios: {
        polling: {
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: SCALE },
                { duration: '2m', target: SCALE },
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
    thresholds: {
        'polling_p': ['p(95)<200', 'p(99)<500'],
        'product_p': ['p(95)<300'],
        'polling_errors': ['rate<0.01'],
    },
};

export function pollPosition_fn() {
    const vu = ((__VU - 1) % SCALE) + 1;
    const res = pollPosition(vu);
    pollDuration.add(res.timings.duration);
    pollErrors.add(res.status >= 400);
    pollCount.add(1);
    check(res, { 'poll: 200': (r) => r.status === 200 });

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
