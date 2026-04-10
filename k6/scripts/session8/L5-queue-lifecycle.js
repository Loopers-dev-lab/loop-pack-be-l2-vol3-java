/**
 * Level 5 — 전체 라이프사이클 (E2E)
 * 목적: N명이 진입 → 폴링 → 토큰 수신 → 주문 완료까지 전체 사이클 측정.
 * 실행: k6 run -e TOTAL=100 k6/scripts/session8/L5-queue-lifecycle.js
 */
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { enterQueue, pollPosition, placeOrder } from './helpers.js';

const TOTAL = parseInt(__ENV.TOTAL || '500');

const lifecycleDuration = new Trend('lifecycle_duration', true);
const conversionRate = new Rate('conversion_rate');
const expiryRate = new Rate('token_expiry_rate');

export const options = {
    scenarios: {
        lifecycle: {
            executor: 'per-vu-iterations',
            vus: TOTAL,
            iterations: 1,
            maxDuration: '10m',
            exec: 'lifecycleFlow_fn',
        },
    },
    thresholds: {
        'conversion_rate': ['rate>0.90'],
        'token_expiry_rate': ['rate<0.05'],
    },
};

export function lifecycleFlow_fn() {
    const start = Date.now();

    // 1. 진입
    enterQueue(__VU);

    // 2. Polling -> 토큰 수신
    let token = null;
    for (let i = 0; i < 300; i++) {
        const res = pollPosition(__VU);
        try {
            const data = JSON.parse(res.body).data;
            if (data.status === 'READY' && data.token) {
                token = data.token;
                break;
            }
        } catch (e) {}
        sleep(2);
    }

    if (!token) {
        expiryRate.add(1);
        conversionRate.add(0);
        return;
    }

    // 3. 주문
    const orderRes = placeOrder(__VU, token);
    const success = orderRes.status === 201;
    conversionRate.add(success ? 1 : 0);
    expiryRate.add(0);
    lifecycleDuration.add(Date.now() - start);
}

export function handleSummary(data) {
    const total = TOTAL;
    const converted = data.metrics.conversion_rate
        ? Math.round(data.metrics.conversion_rate.values.rate * total) : 0;
    const expired = data.metrics.token_expiry_rate
        ? Math.round(data.metrics.token_expiry_rate.values.rate * total) : 0;
    const median = data.metrics.lifecycle_duration
        ? Math.round(data.metrics.lifecycle_duration.values.med) : 0;

    console.log(`\n=== Lifecycle Results (${total} users) ===`);
    console.log(`Orders completed : ${converted} (${(converted/total*100).toFixed(1)}%)`);
    console.log(`Token expired    : ${expired}`);
    console.log(`Median duration  : ${median}ms (${(median/1000).toFixed(1)}s)`);
    console.log(`Theoretical time : ${Math.ceil(total/140)}s (140 TPS)`);
    return {};
}
