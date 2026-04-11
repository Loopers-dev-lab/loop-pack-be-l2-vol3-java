import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// ============================================================
// DB ORDER BY vs Redis ZSET 랭킹 성능 비교
//
// 사전 준비:
//   1. mysql < k6/seed-ranking-data.sql
//   2. bash k6/seed-ranking-redis.sh
//   3. ./gradlew :apps:commerce-api:bootRun
//
// 실행:
//   k6 run k6/ranking-db-vs-zset.js
// ============================================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 커스텀 메트릭: DB vs ZSET 응답 시간을 별도 추적
const dbDuration = new Trend('db_ranking_duration', true);
const zsetDuration = new Trend('zset_ranking_duration', true);
const dbErrors = new Counter('db_ranking_errors');
const zsetErrors = new Counter('zset_ranking_errors');

export const options = {
    scenarios: {
        // Scenario A: DB ORDER BY 기반 랭킹 조회
        db_ranking: {
            executor: 'ramping-vus',
            exec: 'dbRanking',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },    // 워밍업
                { duration: '20s', target: 50 },    // 안정 구간
                { duration: '10s', target: 200 },   // 부하 증가
                { duration: '20s', target: 200 },   // 중간 피크
                { duration: '10s', target: 500 },   // 고부하
                { duration: '20s', target: 500 },   // 최대 피크
                { duration: '10s', target: 0 },     // 종료
            ],
        },
        // Scenario B: Redis ZSET 기반 랭킹 조회
        zset_ranking: {
            executor: 'ramping-vus',
            exec: 'zsetRanking',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 50 },
                { duration: '20s', target: 50 },
                { duration: '10s', target: 200 },
                { duration: '20s', target: 200 },
                { duration: '10s', target: 500 },
                { duration: '20s', target: 500 },
                { duration: '10s', target: 0 },
            ],
        },
    },
    thresholds: {
        'db_ranking_duration': ['p(95)<2000'],   // DB: p95 < 2s (느릴 수 있음)
        'zset_ranking_duration': ['p(95)<100'],  // ZSET: p95 < 100ms
    },
};

// 랜덤 페이지 선택 (1~5페이지, size=20)
function randomPage() {
    return Math.floor(Math.random() * 5) + 1;
}

// Scenario A: DB ORDER BY
export function dbRanking() {
    const page = randomPage();
    const res = http.get(`${BASE_URL}/api/v1/rankings/db?page=${page}&size=20`);

    dbDuration.add(res.timings.duration);

    const ok = check(res, {
        '[DB] 응답 200': (r) => r.status === 200,
        '[DB] 데이터 존재': (r) => {
            const body = JSON.parse(r.body);
            return body.data && body.data.rankings && body.data.rankings.length > 0;
        },
    });

    if (!ok) {
        dbErrors.add(1);
    }

    sleep(0.5);
}

// Scenario B: Redis ZSET
export function zsetRanking() {
    const page = randomPage();
    const res = http.get(`${BASE_URL}/api/v1/rankings?page=${page}&size=20`);

    zsetDuration.add(res.timings.duration);

    const ok = check(res, {
        '[ZSET] 응답 200': (r) => r.status === 200,
        '[ZSET] 데이터 존재': (r) => {
            const body = JSON.parse(r.body);
            return body.data && body.data.rankings && body.data.rankings.length > 0;
        },
    });

    if (!ok) {
        zsetErrors.add(1);
    }

    sleep(0.5);
}
