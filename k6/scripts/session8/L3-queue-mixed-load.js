/**
 * Level 3 — 혼합 부하 (Polling + 주문 + 상품)
 * 목적: DB 커넥션 경합 검증. HikariCP 40 pool에서 폴링+주문 동시 처리.
 * 실행: k6 run -e POLLERS=500 -e ORDERERS=20 k6/scripts/session8/L3-queue-mixed-load.js
 */
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { authHeaders, enterQueue, pollPosition, placeOrder, getProducts, BASE } from './helpers.js';
import http from 'k6/http';
import exec from 'k6/execution';

const POLLERS = parseInt(__ENV.POLLERS || '1000');
const ORDERERS = parseInt(__ENV.ORDERERS || '50');

const pollDuration = new Trend('polling_p', true);
const orderDuration = new Trend('order_p', true);
const productDuration = new Trend('product_p', true);
const orderSuccess = new Rate('order_success');
const orderCount = new Counter('order_total');

export const options = {
    scenarios: {
        enter: {
            executor: 'shared-iterations',
            vus: Math.min(POLLERS, 100),
            iterations: POLLERS,
            maxDuration: '60s',
            exec: 'enter_fn',
        },
        polling: {
            executor: 'constant-vus',
            vus: Math.min(POLLERS, 500),
            duration: '2m',
            startTime: '65s',
            exec: 'poll_fn',
        },
        ordering: {
            executor: 'constant-vus',
            vus: ORDERERS,
            duration: '2m',
            startTime: '65s',
            exec: 'pollAndOrder_fn',
        },
        products: {
            executor: 'constant-vus',
            vus: 30,
            duration: '2m30s',
            startTime: '65s',
            exec: 'productList_fn',
        },
    },
    thresholds: {
        'order_success': ['rate>0.95'],
        'order_p': ['p(95)<500'],
        'polling_p': ['p(95)<100'],
        'product_p': ['p(95)<300'],
    },
};

export function enter_fn() {
    const userIdx = exec.scenario.iterationInTest + 1;
    enterQueue(userIdx);
    sleep(0.05);
}

export function poll_fn() {
    const vu = ((__VU - 1) % POLLERS) + 1;
    const res = pollPosition(vu);
    pollDuration.add(res.timings.duration);
    check(res, { 'poll: 200': (r) => r.status === 200 });
    sleep(2);
}

export function pollAndOrder_fn() {
    const vu = ((__VU - 1) % POLLERS) + 1;
    const res = pollPosition(vu);
    pollDuration.add(res.timings.duration);

    try {
        const data = JSON.parse(res.body).data;
        if (data.status === 'READY' && data.token) {
            const orderRes = placeOrder(vu, data.token);
            orderDuration.add(orderRes.timings.duration);
            orderSuccess.add(orderRes.status === 201 ? 1 : 0);
            orderCount.add(1);
        }
    } catch (e) {}

    sleep(2);
}

export function productList_fn() {
    const res = getProducts();
    productDuration.add(res.timings.duration);
    check(res, { 'product: 200': (r) => r.status === 200 });
    sleep(1);
}
