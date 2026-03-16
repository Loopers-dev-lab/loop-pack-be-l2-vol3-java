import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    scenarios: {
        load_test: {
            executor: 'constant-arrival-rate',
            rate: 100,
            timeUnit: '1s',
            duration: '1m',
            preAllocatedVUs: 50,
            maxVUs: 200,
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

const sorts = ['latest', 'price_asc', 'likes_desc'];

export default function () {
    const sort = sorts[Math.floor(Math.random() * sorts.length)];
    const page = Math.floor(Math.random() * 5); // 0~4 페이지
    const endpoint = __ENV.ENDPOINT || '';
    const url = `${BASE_URL}/api/v1/products${endpoint}?sort=${sort}&page=${page}&size=20`;

    const res = http.get(url);
    check(res, {
        'status 200': (r) => r.status === 200,
        'has data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.meta && body.meta.result === 'SUCCESS';
            } catch (e) {
                return false;
            }
        },
    });
}
