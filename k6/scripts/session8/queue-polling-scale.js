import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const SCALE = parseInt(__ENV.SCALE || '500');

const pollingDuration = new Trend('polling_duration', true);
const productDuration = new Trend('product_duration', true);
const pollingErrors = new Rate('polling_errors');

export const options = {
    scenarios: {
        polling: {
            executor: 'ramping-vus',
            stages: [
                { duration: '30s', target: SCALE },  // ramp-up
                { duration: '2m',  target: SCALE },  // 유지
                { duration: '15s', target: 0 },       // ramp-down
            ],
            exec: 'pollPosition',
        },
        products: {
            executor: 'constant-vus',
            vus: 50,
            duration: '3m',
            exec: 'productList',
        },
    },
    thresholds: {
        'polling_duration': ['p(95)<100'],
        'product_duration': ['p(95)<300'],
        'polling_errors': ['rate<0.01'],
    },
};

function authHeaders(vu) {
    return {
        'X-Loopers-LoginId': `k6user${vu}`,
        'X-Loopers-LoginPw': 'Test1234!',
    };
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
