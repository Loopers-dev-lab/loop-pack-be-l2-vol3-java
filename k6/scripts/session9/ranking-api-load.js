/**
 * L2: 랭킹 API 부하 테스트
 *
 * 목표: 랭킹 페이지 조회 API의 응답 시간과 처리량을 측정한다.
 *       ZREVRANGE(Redis) + findAllByIds(DB) + 브랜드 조합의 복합 조회 성능.
 *
 * 검증 포인트:
 *   - p95 < 50ms (Redis ZREVRANGE O(log N + M) + DB 캐시 히트 기준)
 *   - p99 < 100ms (캐시 미스 포함)
 *   - 에러율 < 0.1%
 *   - 페이지네이션 (page=0~4) 균등 부하
 *   - 랭킹 비어있는 날짜 조회 시에도 정상 응답
 *
 * 전제:
 *   - ZSET에 데이터가 적재된 상태 (L1 또는 seed 실행 후)
 *   - commerce-api, Redis, MySQL 실행 중
 *
 * 실행:
 *   k6 run k6/scripts/session9/ranking-api-load.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TODAY = new Date().toISOString().slice(0, 10).replace(/-/g, '');

// Custom metrics
const rankingLatency = new Trend('ranking_api_latency', true);
const rankingErrors = new Rate('ranking_api_errors');
const rankingRequests = new Counter('ranking_api_requests');

export const options = {
    scenarios: {
        steady: {
            executor: 'constant-vus',
            vus: 50,
            duration: '3m',
        },
    },
    thresholds: {
        'ranking_api_latency': ['p(95)<50', 'p(99)<100'],
        'ranking_api_errors': ['rate<0.001'],
        'http_req_failed': ['rate<0.001'],
    },
};

export default function () {
    const page = Math.floor(Math.random() * 5);  // page 0~4 랜덤
    const size = 20;

    const res = http.get(
        `${BASE_URL}/api/v1/rankings?date=${TODAY}&page=${page}&size=${size}`,
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { name: 'GET /api/v1/rankings' },
        }
    );

    rankingLatency.add(res.timings.duration);
    rankingRequests.add(1);

    const passed = check(res, {
        'status 200': (r) => r.status === 200,
        'has SUCCESS meta': (r) => {
            try {
                return JSON.parse(r.body).meta.result === 'SUCCESS';
            } catch (e) {
                return false;
            }
        },
        'has content array': (r) => {
            try {
                return Array.isArray(JSON.parse(r.body).data.content);
            } catch (e) {
                return false;
            }
        },
    });

    if (!passed) {
        rankingErrors.add(1);
    } else {
        rankingErrors.add(0);
    }

    sleep(0.1); // 100ms 간격
}
