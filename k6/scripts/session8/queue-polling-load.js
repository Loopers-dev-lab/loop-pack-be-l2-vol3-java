import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = parseInt(__ENV.USERS || '1000');

// Custom metrics
const pollingDuration = new Trend('polling_duration', true);
const productDuration = new Trend('product_duration', true);
const enterDuration = new Trend('enter_duration', true);
const pollingErrors = new Rate('polling_errors');

export const options = {
    scenarios: {
        // 1. 대기열 진입 (처음 30초)
        enter: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: USERS,
            maxDuration: '30s',
            exec: 'enterQueue',
        },
        // 2. Polling (30초 후 시작, 2분)
        polling: {
            executor: 'constant-vus',
            vus: USERS,
            duration: '2m',
            startTime: '35s',
            exec: 'pollPosition',
        },
        // 3. 다른 API 혼합 부하
        products: {
            executor: 'constant-vus',
            vus: 50,
            duration: '2m30s',
            exec: 'productList',
        },
    },
    thresholds: {
        'polling_duration': ['p(95)<50', 'p(99)<100'],
        'product_duration': ['p(95)<200'],
        'polling_errors': ['rate<0.001'],
    },
};

function authHeaders(vu) {
    return {
        'X-Loopers-LoginId': `k6user${vu}`,
        'X-Loopers-LoginPw': 'Test1234!',
    };
}

export function enterQueue() {
    const headers = authHeaders(__VU);
    const res = http.post(`${BASE}/api/v1/queue/enter`, null, { headers });
    enterDuration.add(res.timings.duration);
    check(res, { 'enter: 2xx': (r) => r.status >= 200 && r.status < 300 });
}

export function pollPosition() {
    const headers = authHeaders(__VU);
    const res = http.get(`${BASE}/api/v1/queue/position`, { headers });
    pollingDuration.add(res.timings.duration);
    pollingErrors.add(res.status >= 400);
    check(res, { 'polling: 200': (r) => r.status === 200 });
    sleep(2); // 2초 간격 Polling
}

export function productList() {
    const res = http.get(`${BASE}/api/v1/products?page=0&size=20`);
    productDuration.add(res.timings.duration);
    check(res, { 'products: 200': (r) => r.status === 200 });
    sleep(1);
}
