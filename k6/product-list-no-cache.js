import http from 'k6/http';
import { BASE_URL, defaultOptions, checkResponse } from './common.js';

export const options = {
    ...defaultOptions,
    thresholds: {
        ...defaultOptions.thresholds,
        http_req_duration: ['p(95)<300', 'p(99)<500'], // 인덱스만 사용, 캐시 없음
    },
};

const sorts = ['latest', 'price_asc', 'likes_desc'];

export default function () {
    const sort = sorts[Math.floor(Math.random() * sorts.length)];
    const page = Math.floor(Math.random() * 5);
    const url = `${BASE_URL}/api/v1/products/no-cache?sort=${sort}&page=${page}&size=20`;

    const res = http.get(url);
    checkResponse(res, 'no-cache-list');
}
