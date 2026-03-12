import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const defaultOptions = {
    scenarios: {
        load_test: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 50,
            maxVUs: 300,
            stages: [
                { duration: '10s', target: 50 },   // Warm-up
                { duration: '20s', target: 200 },   // Ramp-up
                { duration: '30s', target: 200 },   // Peak
                { duration: '10s', target: 10 },    // Cool-down
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.01'],
    },
};

export function checkResponse(res, name) {
    check(res, {
        [`${name} status 200`]: (r) => r.status === 200,
        [`${name} has data`]: (r) => {
            const body = JSON.parse(r.body);
            return body.meta && body.meta.result === 'SUCCESS';
        },
    });
}
