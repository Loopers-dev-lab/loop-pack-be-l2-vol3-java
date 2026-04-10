import http from 'k6/http';
import { check, sleep } from 'k6';
import { authHeaders, checkResponse } from '../../lib/helpers.js';

export const options = {
    scenarios: {
        list_only: {
            executor: 'constant-vus',
            vus: 30,
            duration: '3m',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<150'],
        http_req_failed: ['rate<0.001'],
    },
};

const BASE = 'http://localhost:8080';
const HEADERS = authHeaders('testuser', 'Test1234!');
const SORTS = ['LATEST', 'PRICE_ASC', 'LIKES_DESC'];

export default function () {
    const rand = Math.random();

    if (rand < 0.6) {
        // 60% — 캐시 대상: 무필터 + 첫 1페이지 (page=0)
        const page = 0;
        const sort = SORTS[Math.floor(Math.random() * 3)];
        const res = http.get(`${BASE}/api/v1/products?page=${page}&size=20&sort=${sort}`,
            { headers: HEADERS });
        check(res, checkResponse(res, 'cached-list'));
    } else if (rand < 0.85) {
        // 25% — 캐시 미대상: keyword 검색
        const keywords = ['나이키', '아디다스', '셔츠', '바지', '가방'];
        const keyword = keywords[Math.floor(Math.random() * keywords.length)];
        const res = http.get(`${BASE}/api/v1/products?q=${encodeURIComponent(keyword)}&size=20`,
            { headers: HEADERS });
        check(res, checkResponse(res, 'search-list'));
    } else {
        // 15% — 캐시 미대상: 2페이지 이후
        const page = Math.floor(Math.random() * 49) + 1;  // page 1~49
        const res = http.get(`${BASE}/api/v1/products?page=${page}&size=20&sort=LATEST`,
            { headers: HEADERS });
        check(res, checkResponse(res, 'deep-page-list'));
    }
    sleep(0.2);
}
