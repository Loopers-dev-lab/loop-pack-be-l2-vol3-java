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
};
