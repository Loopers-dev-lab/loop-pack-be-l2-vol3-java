/**
 * L5: 랭킹 운영 시뮬레이션 (Mixed Load)
 *
 * 목표: 실제 운영 환경을 모사하여 이벤트 발생 + 랭킹 조회 + 상품 상세(rank 포함)를
 *       장시간 동시 실행하며 시스템 안정성을 검증한다.
 *
 * 트래픽 비율:
 *   - 상품 조회 (→ VIEW 이벤트): 50%
 *   - 랭킹 페이지 조회: 25%
 *   - 상품 상세 (→ VIEW 이벤트 + rank 확인): 15%
 *   - 좋아요/취소: 10%
 *
 * 부하 패턴: Ramp-up → Steady → Spike → Steady → Ramp-down
 *   10 VU → 50 VU (1분) → 50 VU (3분) → 100 VU (1분) → 50 VU (3분) → 0
 *
 * 검증 포인트:
 *   - Spike(100 VU) 구간에서도 랭킹 API p95 < 100ms
 *   - 전체 에러율 < 0.5%
 *   - Steady 구간 메모리/커넥션 안정 (Grafana 확인)
 *
 * 실행:
 *   k6 run k6/scripts/session9/ranking-mixed-load.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { getProductIdZipf, authHeaders } from '../../lib/helpers.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MAX_PRODUCT_ID = parseInt(__ENV.MAX_PRODUCT_ID || '100');
const MAX_USER_ID = parseInt(__ENV.MAX_USER_ID || '100');
const TODAY = new Date().toISOString().slice(0, 10).replace(/-/g, '');

const rankingLatency = new Trend('ranking_latency', true);
const detailLatency = new Trend('detail_latency', true);
const overallErrors = new Rate('overall_errors');

export const options = {
    stages: [
        { duration: '30s', target: 10 },   // Warm-up
        { duration: '30s', target: 50 },    // Ramp-up
        { duration: '3m', target: 50 },     // Steady
        { duration: '30s', target: 100 },   // Spike
        { duration: '1m', target: 100 },    // Spike hold
        { duration: '30s', target: 50 },    // Recovery
        { duration: '2m', target: 50 },     // Steady
        { duration: '30s', target: 0 },     // Ramp-down
    ],
    thresholds: {
        'ranking_latency': ['p(95)<100', 'p(99)<200'],
        'detail_latency': ['p(95)<100'],
        'overall_errors': ['rate<0.005'],
        'http_req_duration': ['p(95)<150'],
    },
};

export default function () {
    const userId = Math.floor(Math.random() * MAX_USER_ID) + 1;
    const loginId = `k6rank${userId}`;
    const loginPw = 'password1!';
    const productId = getProductIdZipf(MAX_PRODUCT_ID);

    const rand = Math.random();
    let res;
    let passed;

    if (rand < 0.50) {
        // 50%: 상품 조회 (VIEW 이벤트 발생)
        res = http.get(
            `${BASE_URL}/api/v1/products/${productId}`,
            {
                headers: authHeaders(loginId, loginPw),
                tags: { name: 'product:view' },
            }
        );
        passed = check(res, { 'view ok': (r) => r.status === 200 });

    } else if (rand < 0.75) {
        // 25%: 랭킹 페이지 조회
        const page = Math.floor(Math.random() * 3);
        res = http.get(
            `${BASE_URL}/api/v1/rankings?date=${TODAY}&page=${page}&size=20`,
            {
                headers: { 'Content-Type': 'application/json' },
                tags: { name: 'ranking:list' },
            }
        );
        rankingLatency.add(res.timings.duration);
        passed = check(res, {
            'ranking ok': (r) => r.status === 200,
            'ranking sorted': (r) => {
                try {
                    const items = JSON.parse(r.body).data.content;
                    if (items.length < 2) return true;
                    return items[0].score >= items[1].score;
                } catch (e) {
                    return false;
                }
            },
        });

    } else if (rand < 0.90) {
        // 15%: 상품 상세 (rank 포함 확인)
        res = http.get(
            `${BASE_URL}/api/v1/products/${productId}`,
            {
                headers: authHeaders(loginId, loginPw),
                tags: { name: 'product:detail+rank' },
            }
        );
        detailLatency.add(res.timings.duration);
        passed = check(res, {
            'detail ok': (r) => r.status === 200,
            'detail has rank field': (r) => {
                try {
                    return JSON.parse(r.body).data.hasOwnProperty('rank');
                } catch (e) {
                    return false;
                }
            },
        });

    } else {
        // 10%: 좋아요/취소
        if (Math.random() < 0.7) {
            res = http.post(
                `${BASE_URL}/api/v1/products/${productId}/likes`,
                null,
                {
                    headers: authHeaders(loginId, loginPw),
                    tags: { name: 'like:toggle' },
                }
            );
        } else {
            res = http.del(
                `${BASE_URL}/api/v1/products/${productId}/likes`,
                null,
                {
                    headers: authHeaders(loginId, loginPw),
                    tags: { name: 'unlike:toggle' },
                }
            );
        }
        passed = check(res, {
            'like/unlike ok': (r) => r.status === 200 || r.status === 201,
        });
    }

    overallErrors.add(passed ? 0 : 1);
    sleep(0.1 + Math.random() * 0.2); // 100~300ms 간격 (현실적 유저 행동)
}
