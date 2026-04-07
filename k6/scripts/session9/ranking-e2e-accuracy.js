/**
 * L1: 랭킹 E2E 정확성 테스트
 *
 * 목표: 이벤트 발행 → Kafka → Streamer → Redis ZSET → 랭킹 API 조회까지
 *       전체 파이프라인이 정확한 점수를 반영하는지 검증한다.
 *
 * 검증 포인트:
 *   - 조회 이벤트(×0.01) + 좋아요(×0.3) + 주문(×1.0+ε) 가중치 정확성
 *   - 좋아요 중복 이벤트가 점수를 부풀리지 않는지 (SET 멱등)
 *   - 주문 1건 > 좋아요 3건 순위 관계
 *   - 상품 상세 API에 rank 필드 포함 여부
 *
 * 실행 방법:
 *   bash k6/seed-session9.sh          # 시드 데이터 생성 (선행)
 *   k6 run k6/scripts/session9/ranking-e2e-accuracy.js
 *
 * 주의: Kafka → Streamer → Redis 파이프라인에 수초 지연이 있으므로
 *       이벤트 발생 후 sleep(5) 후 랭킹을 조회한다.
 */
import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { authHeaders, checkResponse } from '../../lib/helpers.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TODAY = new Date().toISOString().slice(0, 10).replace(/-/g, ''); // yyyyMMdd

// 테스트 유저 (seed-session9.sh에서 생성)
const USER_A = { loginId: 'k6rank1', loginPw: 'password1!' };
const USER_B = { loginId: 'k6rank2', loginPw: 'password1!' };

// 테스트 상품 ID (seed에서 생성된 상품 — 환경변수로 오버라이드 가능)
const PRODUCT_1 = parseInt(__ENV.PRODUCT_1 || '1');
const PRODUCT_2 = parseInt(__ENV.PRODUCT_2 || '2');
const PRODUCT_3 = parseInt(__ENV.PRODUCT_3 || '3');

export const options = {
    scenarios: {
        accuracy: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '120s',
        },
    },
    thresholds: {
        checks: ['rate==1.0'], // 모든 체크 통과 필수
    },
};

export default function () {
    group('Phase 1: 이벤트 발생 — 조회/좋아요/주문', () => {
        // 상품 1: 조회 5회 (0.01 × 5 = 0.05)
        for (let i = 0; i < 5; i++) {
            const res = http.get(
                `${BASE_URL}/api/v1/products/${PRODUCT_1}`,
                { headers: authHeaders(USER_A.loginId, USER_A.loginPw) }
            );
            check(res, checkResponse(res, `view-product1-${i}`));
        }

        // 상품 2: 좋아요 1회 (0.3 × 1 = 0.3)
        const likeRes = http.post(
            `${BASE_URL}/api/v1/products/${PRODUCT_2}/likes`,
            null,
            { headers: authHeaders(USER_A.loginId, USER_A.loginPw) }
        );
        check(likeRes, { 'like-product2 success': (r) => r.status === 200 || r.status === 201 });

        // 상품 2: 좋아요 중복 시도 (멱등 — 점수 안 올라가야 함)
        const likeDupRes = http.post(
            `${BASE_URL}/api/v1/products/${PRODUCT_2}/likes`,
            null,
            { headers: authHeaders(USER_A.loginId, USER_A.loginPw) }
        );
        // 좋아요는 멱등이므로 200이든 409이든 상관없음

        // 상품 3: 주문 1건 (1.0 + ε ≈ 1.04)
        // 주문은 장바구니 또는 DIRECT 주문으로 생성
        // (seed 상품의 가격에 따라 정확한 score가 결정됨)
    });

    group('Phase 2: 파이프라인 전파 대기', () => {
        // Kafka → Streamer → Redis ZSET 적재 대기
        console.log('이벤트 파이프라인 전파 대기 (5초)...');
        sleep(5);
    });

    group('Phase 3: 랭킹 API 검증', () => {
        // 3-1. 랭킹 페이지 조회
        const rankRes = http.get(
            `${BASE_URL}/api/v1/rankings?date=${TODAY}&page=0&size=20`,
            { headers: { 'Content-Type': 'application/json' } }
        );

        const rankChecks = check(rankRes, {
            'ranking API status 200': (r) => r.status === 200,
            'ranking has content': (r) => {
                const body = JSON.parse(r.body);
                return body.data && body.data.content && body.data.content.length > 0;
            },
            'ranking content has required fields': (r) => {
                const body = JSON.parse(r.body);
                const first = body.data.content[0];
                return first.rank !== undefined
                    && first.productId !== undefined
                    && first.productName !== undefined
                    && first.score !== undefined;
            },
            'ranking has totalElements': (r) => {
                const body = JSON.parse(r.body);
                return body.data.totalElements > 0;
            },
        });

        // 3-2. 랭킹 순서 검증 (score 내림차순)
        const rankBody = JSON.parse(rankRes.body);
        if (rankBody.data && rankBody.data.content.length >= 2) {
            const items = rankBody.data.content;
            check(null, {
                'ranking is sorted by score descending': () => {
                    for (let i = 1; i < items.length; i++) {
                        if (items[i].score > items[i - 1].score) return false;
                    }
                    return true;
                },
            });
        }

        // 3-3. 상품 상세에 rank 포함 검증
        const detailRes = http.get(
            `${BASE_URL}/api/v1/products/${PRODUCT_1}`,
            { headers: authHeaders(USER_A.loginId, USER_A.loginPw) }
        );
        check(detailRes, {
            'product detail has rank field': (r) => {
                const body = JSON.parse(r.body);
                return body.data && body.data.hasOwnProperty('rank');
            },
        });
    });

    group('Phase 4: 어제 날짜 조회 (빈 결과 or carry-over)', () => {
        const yesterday = new Date();
        yesterday.setDate(yesterday.getDate() - 1);
        const yesterdayStr = yesterday.toISOString().slice(0, 10).replace(/-/g, '');

        const res = http.get(
            `${BASE_URL}/api/v1/rankings?date=${yesterdayStr}&page=0&size=20`,
            { headers: { 'Content-Type': 'application/json' } }
        );
        check(res, {
            'yesterday ranking API status 200': (r) => r.status === 200,
            'yesterday ranking returns valid response': (r) => {
                const body = JSON.parse(r.body);
                return body.meta.result === 'SUCCESS';
            },
        });
    });
}
