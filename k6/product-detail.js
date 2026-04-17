import http from 'k6/http';
import { BASE_URL, defaultOptions, checkResponse } from './common.js';

export const options = {
    ...defaultOptions,
    thresholds: {
        ...defaultOptions.thresholds,
        http_req_duration: ['p(95)<100', 'p(99)<200'],
    },
};

// 1~100 범위의 상품 ID를 랜덤 조회 (시딩 데이터 기준)
const MAX_PRODUCT_ID = __ENV.MAX_PRODUCT_ID ? parseInt(__ENV.MAX_PRODUCT_ID) : 100;

export default function () {
    const productId = Math.floor(Math.random() * MAX_PRODUCT_ID) + 1;
    const url = `${BASE_URL}/api/v1/products/${productId}`;

    const res = http.get(url);
    checkResponse(res, 'product-detail');
}
