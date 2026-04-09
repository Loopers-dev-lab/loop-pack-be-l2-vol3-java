import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// Redis ZSET 기반 랭킹 단독 부하 테스트

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const zsetDuration = new Trend('zset_ranking_duration', true);
const zsetErrors = new Counter('zset_ranking_errors');

export const options = {
    stages: [
        { duration: '10s', target: 50 },
        { duration: '20s', target: 50 },
        { duration: '10s', target: 200 },
        { duration: '20s', target: 200 },
        { duration: '10s', target: 500 },
        { duration: '20s', target: 500 },
        { duration: '10s', target: 0 },
    ],
};

export default function () {
    const page = Math.floor(Math.random() * 5) + 1;
    const res = http.get(`${BASE_URL}/api/v1/rankings?page=${page}&size=20`);

    zsetDuration.add(res.timings.duration);

    const ok = check(res, {
        '[ZSET] 응답 200': (r) => r.status === 200,
        '[ZSET] 데이터 존재': (r) => {
            const body = JSON.parse(r.body);
            return body.data && body.data.rankings && body.data.rankings.length > 0;
        },
    });

    if (!ok) zsetErrors.add(1);
    sleep(0.5);
}
