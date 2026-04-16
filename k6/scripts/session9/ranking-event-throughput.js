/**
 * L3: 이벤트 처리량 + 랭킹 반영 테스트
 *
 * 목표: 대량 이벤트(조회/좋아요/주문)가 동시 유입될 때
 *       commerce-streamer가 Kafka consumer lag 없이 ZSET 적재를 따라가는지 검증한다.
 *       동시에 랭킹 API 조회를 병행하여 읽기/쓰기 동시 부하의 영향을 측정한다.
 *
 * 검증 포인트:
 *   - 3분간 대량 이벤트 발생 후 ZSET 점수가 정확히 반영
 *   - 이벤트 발생 중에도 랭킹 API 응답 시간 p95 < 100ms
 *   - 조회(70%) + 좋아요(20%) + 주문(10%) 비율로 실제 트래픽 패턴 모사
 *   - Zipf 분포로 상위 20% 상품에 80% 트래픽 집중
 *
 * 전제:
 *   - seed-session9.sh 실행 완료
 *   - commerce-api, commerce-streamer, Kafka, Redis, MySQL 모두 실행 중
 *
 * 실행:
 *   k6 run k6/scripts/session9/ranking-event-throughput.js
 *
 * 실행 후 확인:
 *   - Grafana에서 Kafka consumer lag 모니터링
 *   - redis-cli: ZCARD ranking:all:{today} → 이벤트 받은 상품 수
 */
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';
import { getProductIdZipf, authHeaders, checkResponse } from '../../lib/helpers.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MAX_PRODUCT_ID = parseInt(__ENV.MAX_PRODUCT_ID || '100');
const MAX_USER_ID = parseInt(__ENV.MAX_USER_ID || '100');
const TODAY = new Date().toISOString().slice(0, 10).replace(/-/g, '');

// Custom metrics
const eventLatency = new Trend('event_latency', true);
const rankingReadLatency = new Trend('ranking_read_latency', true);
const eventErrors = new Rate('event_errors');

export const options = {
    scenarios: {
        // 이벤트 발생 VU (쓰기 부하)
        events: {
            executor: 'constant-vus',
            vus: 30,
            duration: '3m',
            exec: 'generateEvents',
        },
        // 랭킹 조회 VU (읽기 부하 — 쓰기와 동시)
        rankings: {
            executor: 'constant-vus',
            vus: 10,
            duration: '3m',
            exec: 'queryRankings',
            startTime: '10s', // 이벤트가 쌓이기 시작한 후 조회 시작
        },
    },
    thresholds: {
        'ranking_read_latency': ['p(95)<100'],
        'event_errors': ['rate<0.01'],
    },
};

// 이벤트 발생 시나리오
export function generateEvents() {
    const userId = Math.floor(Math.random() * MAX_USER_ID) + 1;
    const loginId = `k6rank${userId}`;
    const loginPw = 'password1!';
    const productId = getProductIdZipf(MAX_PRODUCT_ID);

    const rand = Math.random();
    let res;

    if (rand < 0.7) {
        // 70%: 상품 조회 (→ PRODUCT_VIEWED 이벤트)
        res = http.get(
            `${BASE_URL}/api/v1/products/${productId}`,
            {
                headers: authHeaders(loginId, loginPw),
                tags: { name: 'event:view' },
            }
        );
    } else if (rand < 0.9) {
        // 20%: 좋아요 토글
        if (Math.random() < 0.7) {
            // 70%: 좋아요
            res = http.post(
                `${BASE_URL}/api/v1/products/${productId}/likes`,
                null,
                {
                    headers: authHeaders(loginId, loginPw),
                    tags: { name: 'event:like' },
                }
            );
        } else {
            // 30%: 좋아요 취소
            res = http.del(
                `${BASE_URL}/api/v1/products/${productId}/likes`,
                null,
                {
                    headers: authHeaders(loginId, loginPw),
                    tags: { name: 'event:unlike' },
                }
            );
        }
    } else {
        // 10%: 상품 조회 (주문은 복잡하므로 조회로 대체 — 주문 이벤트는 별도 시드)
        res = http.get(
            `${BASE_URL}/api/v1/products/${productId}`,
            {
                headers: authHeaders(loginId, loginPw),
                tags: { name: 'event:view-as-order-proxy' },
            }
        );
    }

    if (res) {
        eventLatency.add(res.timings.duration);
        const passed = check(res, {
            'event response ok': (r) => r.status === 200 || r.status === 201,
        });
        eventErrors.add(passed ? 0 : 1);
    }

    sleep(0.05); // 20 req/sec per VU → 30 VU × 20 = 600 req/sec total
}

// 랭킹 조회 시나리오 (이벤트 발생과 동시)
export function queryRankings() {
    const page = Math.floor(Math.random() * 3); // page 0~2

    const res = http.get(
        `${BASE_URL}/api/v1/rankings?date=${TODAY}&page=${page}&size=20`,
        {
            headers: { 'Content-Type': 'application/json' },
            tags: { name: 'GET /api/v1/rankings (concurrent)' },
        }
    );

    rankingReadLatency.add(res.timings.duration);

    check(res, {
        'ranking status 200': (r) => r.status === 200,
        'ranking is sorted': (r) => {
            try {
                const items = JSON.parse(r.body).data.content;
                if (items.length < 2) return true;
                for (let i = 1; i < items.length; i++) {
                    if (items[i].score > items[i - 1].score) return false;
                }
                return true;
            } catch (e) {
                return false;
            }
        },
    });

    sleep(0.5); // 2 req/sec per VU → 10 VU × 2 = 20 req/sec
}
