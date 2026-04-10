import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '1';

const tokenReceived = new Counter('token_received');
const tokenExpired = new Counter('token_expired');

export const options = {
    scenarios: {
        ttl: {
            executor: 'constant-vus',
            vus: 50,
            duration: '3m',
        },
    },
};

export function setup() {
    const results = [];
    for (let i = 1; i <= 50; i++) {
        const res = http.post(
            `${BASE_URL}/api/v1/queue/${EVENT_ID}/enter`,
            null,
            { headers: { 'X-User-Id': String(i) } }
        );
        results.push({ userId: i, status: res.status });
    }
    return { users: results.filter((r) => r.status === 200) };
}

export default function (data) {
    const userIndex = __VU % data.users.length;
    const userId = data.users[userIndex].userId;

    const res = http.get(
        `${BASE_URL}/api/v1/queue/${EVENT_ID}/position`,
        { headers: { 'X-User-Id': String(userId) } }
    );

    check(res, { 'status is 200': (r) => r.status === 200 });

    if (res.status === 200) {
        const body = res.json();
        const token = body.data && body.data.token;

        if (token && token.length > 0) {
            tokenReceived.add(1);
        }

        const position = body.data && body.data.position;
        if (position === -1 || position === null) {
            tokenExpired.add(1);
        }
    }

    sleep(5);
}
