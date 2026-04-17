import http from 'k6/http';
import { BASE_URL, defaultOptions, checkResponse } from './common.js';

export const options = {
    ...defaultOptions,
    thresholds: {
        ...defaultOptions.thresholds,
        http_req_duration: ['p(95)<2000', 'p(99)<5000'], // AS-IS: 전량 로딩 + COUNT + in-memory sort
    },
};

const sorts = ['latest', 'price_asc', 'likes_desc'];

export default function () {
    const sort = sorts[Math.floor(Math.random() * sorts.length)];
    const url = `${BASE_URL}/api/v1/products/no-optimization?sort=${sort}`;

    const res = http.get(url);
    checkResponse(res, 'no-optimization-list');
}
