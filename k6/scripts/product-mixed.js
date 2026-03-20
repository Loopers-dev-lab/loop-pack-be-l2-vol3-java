import http from 'k6/http';
import { check, sleep } from 'k6';
import { getProductIdZipf, authHeaders, adminHeaders, checkResponse } from '../lib/helpers.js';

export const options = {
    scenarios: {
        readers: {
            executor: 'constant-vus',
            vus: 35,
            duration: '5m',
            exec: 'readScenario',
        },
        list_readers: {
            executor: 'constant-vus',
            vus: 10,
            duration: '5m',
            exec: 'listScenario',
        },
        admin_writers: {
            executor: 'constant-vus',
            vus: 3,
            duration: '5m',
            exec: 'writeScenario',
        },
        orderers: {
            executor: 'constant-vus',
            vus: 2,
            duration: '5m',
            exec: 'orderScenario',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{scenario:readers}': ['p(95)<100'],
    },
};

const BASE = 'http://localhost:8080';
const CUSTOMER_HEADERS = authHeaders('testuser', 'Test1234!');
const ADMIN_HEADERS = adminHeaders();

// 70% — 고객 상품 상세 조회
export function readScenario() {
    const productId = getProductIdZipf(100);
    const res = http.get(`${BASE}/api/v1/products/${productId}`, { headers: CUSTOMER_HEADERS });
    check(res, checkResponse(res, 'read'));
    sleep(0.1);
}

// 20% — 고객 상품 목록 조회
export function listScenario() {
    const page = Math.floor(Math.random() * 5);
    const sort = ['LATEST', 'PRICE_ASC', 'LIKES_DESC'][Math.floor(Math.random() * 3)];
    const res = http.get(`${BASE}/api/v1/products?page=${page}&size=20&sort=${sort}`,
        { headers: CUSTOMER_HEADERS });
    check(res, checkResponse(res, 'list'));
    sleep(0.5);
}

// 5% — 관리자 상품 수정 (Cache Evict 유발)
export function writeScenario() {
    const productId = Math.floor(Math.random() * 100) + 1;
    const payload = JSON.stringify({
        productName: `Updated-${Date.now()}`,
        price: Math.floor(Math.random() * 100000) + 1000,
        description: 'k6 load test update',
    });
    const res = http.put(`${BASE}/api-admin/v1/products/${productId}`,
        payload, { headers: ADMIN_HEADERS });
    check(res, { 'admin-update 200': (r) => r.status === 200 });
    sleep(2);  // 관리자는 느리게
}

// 5% — 고객 주문 (재고 hold)
export function orderScenario() {
    const productId = getProductIdZipf(100);
    const payload = JSON.stringify({
        orderType: 'DIRECT',
        items: [{ productId: productId, quantity: 1 }],
    });
    const res = http.post(`${BASE}/api/v1/orders`, payload, { headers: CUSTOMER_HEADERS });
    // 재고 부족은 정상 응답이므로 status만 확인
    check(res, { 'order not 500': (r) => r.status !== 500 });
    sleep(1);
}
