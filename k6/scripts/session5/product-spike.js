import http from 'k6/http';
import { check, sleep } from 'k6';
import { authHeaders, checkResponse } from '../../lib/helpers.js';

export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 10 },     // 워밍업
                { duration: '5s',  target: 200 },     // 5초 만에 200명 급증
                { duration: '30s', target: 200 },     // 30초 유지
                { duration: '10s', target: 0 },        // 쿨다운
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(99)<1000'],
        http_req_failed: ['rate<0.01'],
    },
};

const BASE = 'http://localhost:8080';
const HEADERS = authHeaders('testuser', 'Test1234!');
const HOT_PRODUCT_ID = 1;  // 단일 상품 집중

export default function () {
    // 90% 동일 상품 (Hot Key), 10% 랜덤
    const productId = Math.random() < 0.9 ? HOT_PRODUCT_ID : Math.floor(Math.random() * 100) + 1;
    const res = http.get(`${BASE}/api/v1/products/${productId}`, { headers: HEADERS });
    check(res, checkResponse(res, 'spike-product'));
    sleep(0.05);
}
