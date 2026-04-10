const ADMIN_HEADER = { 'X-Loopers-Ldap': 'loopers.admin' };

async function request(method, url, body = null) {
    const opts = {
        method,
        headers: { ...ADMIN_HEADER, 'Content-Type': 'application/json' },
    };
    if (body) opts.body = JSON.stringify(body);

    const res = await fetch(url, opts);
    const json = await res.json();

    if (json.meta.result !== 'SUCCESS') {
        throw new Error(json.meta.message || json.meta.errorCode || 'API 요청 실패');
    }
    return json.data;
}

// === Brands ===
export const BrandApi = {
    list: (page = 0, size = 20) => request('GET', `/api-admin/v1/brands?page=${page}&size=${size}`),
    get: (id) => request('GET', `/api-admin/v1/brands/${id}`),
    create: (name) => request('POST', '/api-admin/v1/brands', { name }),
    update: (id, name) => request('PUT', `/api-admin/v1/brands/${id}`, { name }),
    delete: (id) => request('DELETE', `/api-admin/v1/brands/${id}`),
};

// === Products ===
export const ProductApi = {
    list: (page = 0, size = 20, brandId = null) => {
        let url = `/api-admin/v1/products?page=${page}&size=${size}`;
        if (brandId) url += `&brandId=${brandId}`;
        return request('GET', url);
    },
    get: (id) => request('GET', `/api-admin/v1/products/${id}`),
    create: (data) => request('POST', '/api-admin/v1/products', data),
    update: (id, data) => request('PUT', `/api-admin/v1/products/${id}`, data),
    delete: (id) => request('DELETE', `/api-admin/v1/products/${id}`),
};

// === Orders ===
export const OrderApi = {
    list: (page = 0, size = 20) => request('GET', `/api-admin/v1/orders?page=${page}&size=${size}`),
    get: (id) => request('GET', `/api-admin/v1/orders/${id}`),
};

// === Coupons ===
export const CouponApi = {
    list: (page = 0, size = 20) => request('GET', `/api-admin/v1/coupons?page=${page}&size=${size}`),
    get: (id) => request('GET', `/api-admin/v1/coupons/${id}`),
    create: (data) => request('POST', '/api-admin/v1/coupons', data),
    update: (id, data) => request('PUT', `/api-admin/v1/coupons/${id}`, data),
    delete: (id) => request('DELETE', `/api-admin/v1/coupons/${id}`),
    issues: (id, page = 0, size = 20) => request('GET', `/api-admin/v1/coupons/${id}/issues?page=${page}&size=${size}`),
};

// === Users ===
export const UserApi = {
    list: (page = 0, size = 20) => request('GET', `/api-admin/v1/users?page=${page}&size=${size}`),
    addPoint: (userId, amount) => request('POST', `/api-admin/v1/users/${userId}/point`, { amount }),
    addPointAll: (amount) => request('POST', '/api-admin/v1/users/point', { amount }),
};

// === Queue (유저측 API — 인증 헤더 직접 전달) ===
export const QueueApi = {
    signup: (loginId, password, name, email) =>
        fetch('/api/v1/users/signup', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ loginId, password, name, birthDate: '20000101', email }),
        }).then(r => r.json()).then(j => {
            if (j.meta.result !== 'SUCCESS') throw new Error(j.meta.message);
            return j.data;
        }),
    enter: (loginId, password) => {
        const opts = {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Loopers-LoginId': loginId,
                'X-Loopers-LoginPw': password,
            },
        };
        return fetch('/api/v1/queue/enter', opts).then(r => r.json()).then(j => {
            if (j.meta.result !== 'SUCCESS') throw new Error(j.meta.message);
            return j.data;
        });
    },
    position: (loginId, password) => {
        return fetch('/api/v1/queue/position', {
            headers: { 'X-Loopers-LoginId': loginId, 'X-Loopers-LoginPw': password },
        }).then(r => r.json()).then(j => j.data);
    },
};

// === Data Generator ===
export const DataGenApi = {
    stats: () => request('GET', '/api-admin/v1/data-generator/stats'),
    bulkInit: () => request('POST', '/api-admin/v1/data-generator/bulk-init'),
    generateLikes: (productIds, likesPerProduct) =>
        request('POST', '/api-admin/v1/data-generator/likes', { productIds, likesPerProduct }),
    generateUsers: (prefix, count, defaultPoint) =>
        request('POST', '/api-admin/v1/data-generator/users', { prefix, count, defaultPoint }),
    generateOrders: (data) =>
        request('POST', '/api-admin/v1/data-generator/orders', data),
    generateCoupons: (data) =>
        request('POST', '/api-admin/v1/data-generator/coupons', data),
    bulkQueueEnter: (prefix, count) =>
        request('POST', '/api-admin/v1/data-generator/queue-enter', { prefix, count }),
};
