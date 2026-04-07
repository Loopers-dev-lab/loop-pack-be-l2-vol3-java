/**
 * L4: 가중치 정확성 + 동시성 테스트
 *
 * 목표: 여러 유저가 동시에 같은 상품에 이벤트를 발생시킬 때
 *       가중치 합산이 정확하고 순위 관계가 올바른지 검증한다.
 *
 * 시나리오:
 *   Phase A — 상품 A에 집중 좋아요 (10 VU × 1회씩 = 10건, score += 3.0)
 *   Phase B — 상품 B에 집중 조회 (10 VU × 50회씩 = 500건, score += 5.0)
 *   Phase C — 전파 대기 후 순위 검증
 *
 *   기대: 상품 B(5.0) > 상품 A(3.0) — 조회 500건이 좋아요 10건보다 높은 이유는
 *          0.01 × 500 = 5.0 vs 0.3 × 10 = 3.0
 *          → 가중치가 낮아도 물량으로 역전 가능함을 검증
 *
 * 전제:
 *   - seed-session9.sh 실행 완료
 *   - 오늘 ZSET이 비어있는 상태에서 시작 (또는 reset 후)
 *
 * 실행:
 *   k6 run k6/scripts/session9/ranking-weight-accuracy.js
 */
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { authHeaders } from '../../lib/helpers.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_A = parseInt(__ENV.PRODUCT_A || '1');  // 좋아요 대상
const PRODUCT_B = parseInt(__ENV.PRODUCT_B || '2');  // 조회 대상
const TODAY = new Date().toISOString().slice(0, 10).replace(/-/g, '');

export const options = {
    scenarios: {
        // Phase A: 상품 A에 10명이 동시 좋아요
        likes: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 1,
            exec: 'likeProductA',
            maxDuration: '30s',
        },
        // Phase B: 상품 B에 10명이 각 50회 조회
        views: {
            executor: 'per-vu-iterations',
            vus: 10,
            iterations: 50,
            exec: 'viewProductB',
            startTime: '5s', // Phase A 후 시작
            maxDuration: '60s',
        },
        // Phase C: 순위 검증
        verify: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            exec: 'verifyRanking',
            startTime: '75s', // Phase B 끝 + 파이프라인 전파 대기
            maxDuration: '30s',
        },
    },
    thresholds: {
        checks: ['rate>0.95'],
    },
};

export function likeProductA() {
    const userId = __VU; // VU ID로 유저 구분 (멱등 테스트 — 같은 유저가 2번 좋아요 안 됨)
    const loginId = `k6rank${userId}`;

    const res = http.post(
        `${BASE_URL}/api/v1/products/${PRODUCT_A}/likes`,
        null,
        { headers: authHeaders(loginId, 'password1!') }
    );

    check(res, {
        'like success': (r) => r.status === 200 || r.status === 201,
    });
}

export function viewProductB() {
    const userId = __VU;
    const loginId = `k6rank${userId}`;

    const res = http.get(
        `${BASE_URL}/api/v1/products/${PRODUCT_B}`,
        { headers: authHeaders(loginId, 'password1!') }
    );

    check(res, {
        'view success': (r) => r.status === 200,
    });

    sleep(0.1);
}

export function verifyRanking() {
    console.log('파이프라인 전파 대기 (5초)...');
    sleep(5);

    const res = http.get(
        `${BASE_URL}/api/v1/rankings?date=${TODAY}&page=0&size=20`,
        { headers: { 'Content-Type': 'application/json' } }
    );

    const body = JSON.parse(res.body);
    const content = body.data.content;

    check(null, {
        'ranking has results': () => content.length >= 2,
    });

    if (content.length >= 2) {
        // 상품 A와 B의 점수 추출
        const productAEntry = content.find(item => item.productId === PRODUCT_A);
        const productBEntry = content.find(item => item.productId === PRODUCT_B);

        if (productAEntry && productBEntry) {
            console.log(`Product A (likes): score=${productAEntry.score}, rank=${productAEntry.rank}`);
            console.log(`Product B (views): score=${productBEntry.score}, rank=${productBEntry.rank}`);

            check(null, {
                // 10명 좋아요: 0.3 × 10 = 3.0
                'product A score ≈ 3.0 (10 likes × 0.3)': () =>
                    productAEntry.score >= 2.5 && productAEntry.score <= 3.5,

                // 500회 조회: 0.01 × 500 = 5.0
                'product B score ≈ 5.0 (500 views × 0.01)': () =>
                    productBEntry.score >= 4.0 && productBEntry.score <= 6.0,

                // 조회 물량이 좋아요를 역전
                'product B > product A (views beat likes by volume)': () =>
                    productBEntry.score > productAEntry.score,
            });
        } else {
            console.log('WARNING: Product A or B not found in ranking');
            console.log('Content:', JSON.stringify(content.slice(0, 5)));
        }
    }
}
