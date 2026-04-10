import { Auth } from './auth.js';

async function request(method, url, body = null, requireAuth = false, extraHeaders = {}) {
    const headers = { 'Content-Type': 'application/json', ...extraHeaders };
    if (requireAuth) Object.assign(headers, Auth.getHeaders());

    const opts = { method, headers };
    if (body) opts.body = JSON.stringify(body);

    const res = await fetch(url, opts);
    const json = await res.json();

    if (json.meta.result !== 'SUCCESS') {
        throw new Error(json.meta.message || json.meta.errorCode || 'API 요청 실패');
    }
    return json.data;
}

// === Products ===
export const ProductApi = {
    list: (page = 0, size = 20, brandId = null, sort = null) => {
        let url = `/api/v1/products?page=${page}&size=${size}`;
        if (brandId) url += `&brandId=${brandId}`;
        if (sort) url += `&sort=${sort}`;
        return request('GET', url);
    },
    get: (id) => request('GET', `/api/v1/products/${id}`),
};

// === Brands (admin API for filter list) ===
const ADMIN_HEADER = { 'X-Loopers-Ldap': 'loopers.admin' };
export const BrandApi = {
    list: (page = 0, size = 200) =>
        request('GET', `/api-admin/v1/brands?page=${page}&size=${size}`, null, false, ADMIN_HEADER),
};

// === Likes ===
export const LikeApi = {
    add: (productId) => request('POST', `/api/v1/products/${productId}/likes`, null, true),
    remove: (productId) => request('DELETE', `/api/v1/products/${productId}/likes`, null, true),
    myList: () => request('GET', '/api/v1/users/me/likes', null, true),
};

// === Orders ===
export const OrderApi = {
    create: (data) => request('POST', '/api/v1/orders', data, true),
    list: (startAt = null, endAt = null) => {
        const start = startAt || new Date(Date.now() - 365 * 24 * 60 * 60 * 1000).toISOString();
        const end = endAt || new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
        return request('GET', `/api/v1/orders?startAt=${encodeURIComponent(start)}&endAt=${encodeURIComponent(end)}`, null, true);
    },
    get: (id) => request('GET', `/api/v1/orders/${id}`, null, true),
    cancelItem: (orderId, orderItemId) =>
        request('PATCH', `/api/v1/orders/${orderId}/items/${orderItemId}/cancel`, null, true),
};

// === Coupons ===
export const CouponApi = {
    available: (page = 0, size = 20) =>
        request('GET', `/api-admin/v1/coupons?page=${page}&size=${size}`, null, false, ADMIN_HEADER),
    issue: (couponId) => request('POST', `/api/v1/coupons/${couponId}/issue`, null, true),
    myList: () => request('GET', '/api/v1/users/me/coupons', null, true),
};

// === Queue ===
export const QueueApi = {
    enter: () => request('POST', '/api/v1/queue/enter', null, true),
    position: () => request('GET', '/api/v1/queue/position', null, true),
    cancel: () => request('DELETE', '/api/v1/queue/cancel', null, true),
    order: (data, token) => request('POST', '/api/v1/orders', data, true, { 'X-Entry-Token': token }),
};

// === Payments ===
export const PaymentApi = {
    request: (data) => request('POST', '/api/v1/payments', data, true),
    status: (orderId) => request('GET', `/api/v1/payments/status?orderId=${orderId}`, null, true),
};

// === Rankings ===
export const RankingApi = {
    list: (date = null, page = 1, size = 20) => {
        let url = `/api/v1/rankings?page=${page}&size=${size}`;
        if (date) url += `&date=${date}`;
        return request('GET', url);
    },
};

// === Users ===
export const UserApi = {
    signup: (data) => request('POST', '/api/v1/users/signup', data),
    me: () => request('GET', '/api/v1/users/me', null, true),
    changePassword: (data) => request('PATCH', '/api/v1/users/password', data, true),
    getPoint: () => request('GET', '/api/v1/users/me/point', null, true),
    chargePoint: (amount) => request('POST', '/api/v1/users/me/point', { amount }, true),
};
