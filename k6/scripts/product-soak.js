import http from 'k6/http';
import { check, sleep } from 'k6';
import { getProductIdZipf, authHeaders, checkResponse } from '../lib/helpers.js';

export const options = {
    scenarios: {
        soak: {
            executor: 'constant-vus',
            vus: 100,
            duration: '30m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<100', 'p(99)<300'],
        http_req_failed: ['rate<0.001'],
    },
};

const BASE = 'http://localhost:8080';
const HEADERS = authHeaders('testuser', 'Test1234!');

export default function () {
    const productId = getProductIdZipf(100);
    const res = http.get(`${BASE}/api/v1/products/${productId}`, { headers: HEADERS });
    check(res, checkResponse(res, 'soak'));
    sleep(0.1);
}
