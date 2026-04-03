import http from 'k6/http';

export const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export function authHeaders(vu) {
    return {
        'X-Loopers-LoginId': `k6user${vu}`,
        'X-Loopers-LoginPw': 'Test1234!',
        'Content-Type': 'application/json',
    };
}

export function enterQueue(vu) {
    return http.post(`${BASE}/api/v1/queue/enter`, null, {
        headers: authHeaders(vu),
    });
}

export function pollPosition(vu) {
    return http.get(`${BASE}/api/v1/queue/position`, {
        headers: authHeaders(vu),
    });
}

export function placeOrder(vu, token, productId) {
    const headers = { ...authHeaders(vu), 'X-Entry-Token': token };
    return http.post(`${BASE}/api/v1/orders`,
        JSON.stringify({
            orderType: 'DIRECT',
            items: [{ productId: productId || 1, quantity: 1 }],
        }),
        { headers }
    );
}

export function getProducts() {
    return http.get(`${BASE}/api/v1/products?page=0&size=20`);
}
