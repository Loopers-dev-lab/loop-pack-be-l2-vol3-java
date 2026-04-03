import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EVENT_ID = __ENV.EVENT_ID || '1';

const positionTrend = new Trend('queue_position');

export const options = {
    scenarios: {
        scheduler: {
            executor: 'constant-vus',
            vus: 100,
            duration: '5m',
        },
    },
};

export function setup() {
    const results = [];
    for (let i = 1; i <= 500; i++) {
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
        const position = body.data && body.data.position;
        if (position !== null && position !== undefined) {
            positionTrend.add(position);
        }
    }

    sleep(3);
}
