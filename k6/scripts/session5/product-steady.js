import http from 'k6/http';
import { check, sleep } from 'k6';
import { getProductIdZipf, authHeaders, checkResponse } from '../../lib/helpers.js';

export const options = {
    scenarios: {
        steady: {
            executor: 'constant-vus',
            vus: 50,
            duration: '5m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<100', 'p(99)<200'],
        http_req_failed: ['rate<0.001'],
    },
};

const BASE = 'http://localhost:8080';
const HEADERS = authHeaders('testuser', 'Test1234!');

export default function () {
    // 80% 상품 상세, 20% 상품 목록
    if (Math.random() < 0.8) {
        const productId = getProductIdZipf(100);
        const res = http.get(`${BASE}/api/v1/products/${productId}`, { headers: HEADERS });
        check(res, checkResponse(res, 'product-detail'));
    } else {
        const page = Math.floor(Math.random() * 5);
        const sort = ['LATEST', 'PRICE_ASC', 'LIKES_DESC'][Math.floor(Math.random() * 3)];
        const res = http.get(`${BASE}/api/v1/products?page=${page}&size=20&sort=${sort}`, { headers: HEADERS });
        check(res, checkResponse(res, 'product-list'));
    }
    sleep(0.1);
}
