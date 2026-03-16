import http from 'k6/http';
import { BASE_URL, defaultOptions, checkResponse } from './common.js';

export const options = {
    ...defaultOptions,
    thresholds: {
        ...defaultOptions.thresholds,
        http_req_duration: ['p(95)<100', 'p(99)<200'], // 캐시 적용 시 더 빠른 응답 기대
    },
};

const sorts = ['latest', 'price_asc', 'likes_desc'];

export default function () {
    const sort = sorts[Math.floor(Math.random() * sorts.length)];
    const page = Math.floor(Math.random() * 5); // 0~4 페이지
    const url = `${BASE_URL}/api/v1/products?sort=${sort}&page=${page}&size=20`;

    const res = http.get(url);
    checkResponse(res, 'optimized-list');
}
