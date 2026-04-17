import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '1';

const successCount = new Counter('queue_enter_success');
const conflictCount = new Counter('queue_enter_conflict');

export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 500 },
                { duration: '1m', target: 500 },
                { duration: '30s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
    },
};

export default function () {
    const userId = __VU * 10000 + __ITER;

    const res = http.post(
        `${BASE_URL}/api/v1/queue/${EVENT_ID}/enter`,
        null,
        {
            headers: { 'X-User-Id': String(userId) },
        }
    );

    if (check(res, { 'status is 200': (r) => r.status === 200 })) {
        successCount.add(1);
    }

    if (check(res, { 'status is 409': (r) => r.status === 409 })) {
        conflictCount.add(1);
    }
}
