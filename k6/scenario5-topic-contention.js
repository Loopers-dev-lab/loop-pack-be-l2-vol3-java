/**
 * 시나리오 5: 토픽 혼재 부하 비교 (VIEW vs LIKE 이벤트 경합)
 *
 * 목적:
 *   PRODUCT_VIEWED + LIKE_CREATED 이벤트가 동일 catalog-events 토픽에 혼재될 때
 *   각 이벤트의 처리 처리량(throughput) 비교.
 *   VIEW 이벤트 폭주 시 LIKE 이벤트가 consumer 배치에서 밀려 처리 지연이 발생하는지 확인.
 *
 * 비교 방법:
 *   - Before: 현재 구조 (catalog-events 단일 토픽)
 *   - After : view-events 토픽 분리 후 동일 조건 실행
 *   테스트 종료 후 DB ranking_metrics 조회하여 view_count vs like_count 누적 비율 비교.
 *
 * 전제:
 *   - commerce-api, commerce-streamer 실행 중
 *   - seed-users.js로 k6u1~k6u500 생성 완료
 *   - products 테이블에 ID 1~9 존재
 *
 * 실행:
 *   # 1. 유저/인증 준비 (최초 1회)
 *   k6 run k6/seed-users.js -e TOTAL=500
 *   k6 run k6/warmup-auth.js -e TOTAL=500 -e WARMUP_VUS=50
 *
 *   # 2. 시나리오 실행
 *   k6 run k6/scenario5-topic-contention.js
 *
 *   # 3. 테스트 종료 후 DB에서 처리 결과 확인
 *   SELECT product_id, view_count, like_count, metrics_date
 *   FROM ranking_metrics
 *   WHERE metrics_date = CURDATE()
 *   ORDER BY product_id;
 *
 * 환경 변수:
 *   VIEW_VUS   : VIEW 이벤트 발생 VU 수 (기본: 100)
 *   LIKE_VUS   : LIKE 이벤트 발생 VU 수 (기본: 50)
 *   DURATION   : 테스트 지속 시간 (기본: 60s)
 *   LIKE_USERS : LIKE 테스트용 유저 수 (기본: 500, seed-users.js TOTAL과 일치해야 함)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8080';
const VIEW_VUS    = parseInt(__ENV.VIEW_VUS    || '100');
const LIKE_VUS    = parseInt(__ENV.LIKE_VUS    || '50');
const DURATION    = __ENV.DURATION    || '60s';
const LIKE_USERS  = parseInt(__ENV.LIKE_USERS  || '500');

// 테스트 대상 상품 (DB에 실제 존재하는 ID)
const PRODUCT_IDS = [1, 2, 3, 4, 5, 7, 8, 9, 10];

// 커스텀 메트릭
const viewResponseTime = new Trend('view_event_response_time_ms', true);
const likeResponseTime = new Trend('like_event_response_time_ms', true);
const viewSuccessRate  = new Rate('view_event_success_rate');
const likeSuccessRate  = new Rate('like_event_success_rate');
const viewEventsSent   = new Counter('view_events_sent');
const likeEventsSent   = new Counter('like_events_sent');

export const options = {
    scenarios: {
        // VIEW 이벤트 폭주: 상품 조회 API 반복 호출 → PRODUCT_VIEWED 이벤트 → catalog-events
        view_flood: {
            executor: 'constant-vus',
            vus: VIEW_VUS,
            duration: DURATION,
            exec: 'generateViewEvents',
        },
        // LIKE 이벤트 발생: LIKE 생성/취소 교대 → LIKE_CREATED/CANCELLED → catalog-events
        like_flood: {
            executor: 'constant-vus',
            vus: LIKE_VUS,
            duration: DURATION,
            exec: 'generateLikeEvents',
        },
    },
    thresholds: {
        // VIEW 이벤트: 상품 조회 API는 빨라야 함
        'view_event_response_time_ms': ['p(95)<200', 'p(99)<500'],
        'view_event_success_rate':     ['rate>0.99'],
        // LIKE 이벤트: VIEW 폭주에도 성공률 유지 여부 확인
        'like_event_success_rate':     ['rate>0.95'],
        // 에러율
        'http_req_failed':             ['rate<0.05'],
    },
};

/**
 * VIEW 이벤트 생성 — 상품 조회 API 반복 호출 (인증 불필요)
 * PRODUCT_VIEWED 이벤트가 catalog-events 토픽으로 직접 발행됨.
 */
export function generateViewEvents() {
    const productId = PRODUCT_IDS[__VU % PRODUCT_IDS.length];
    const res = http.get(`${BASE_URL}/api/v1/products/${productId}`);

    const ok = check(res, {
        'view status 200': (r) => r.status === 200,
    });

    viewSuccessRate.add(ok ? 1 : 0);
    viewResponseTime.add(res.timings.duration);
    viewEventsSent.add(1);

    if (!ok) {
        console.warn(`[VIEW] 상품 조회 실패: productId=${productId}, status=${res.status}`);
    }
}

/**
 * LIKE 이벤트 생성 — LIKE 생성만 (취소 없음, like_count 순증가 확인용)
 * LIKE_CREATED 이벤트가 catalog-events 토픽(outbox)으로 발행됨.
 *
 * 중복 좋아요 방지: (유저, 상품) 조합을 이터레이션마다 다르게 사용.
 * - VU 1~50, ITER 0~N → 고유 (userIdx, productIdx) 조합 보장
 * - LIKE_USERS(500) × PRODUCT_IDS.length(9) = 4,500 고유 조합
 */
export function generateLikeEvents() {
    // 이터레이션마다 고유한 (유저, 상품) 조합 — 중복 LIKE 방지
    const combo     = (__VU - 1) * 1000 + __ITER;  // VU 50개 × 최대 1000 iter
    const userIdx   = (combo % LIKE_USERS) + 1;
    const productId = PRODUCT_IDS[Math.floor(combo / LIKE_USERS) % PRODUCT_IDS.length];
    const loginId   = `k6u${userIdx}`;
    const headers   = {
        'X-Loopers-LoginId': loginId,
        'X-Loopers-LoginPw': 'K6seed1234',
        'Content-Type': 'application/json',
    };

    // LIKE 생성만 (취소 없음 — like_count 순증가 확인용)
    // 409 Conflict(이미 좋아요)는 허용 — 중복 조합 발생 시 무시
    const res = http.post(
        `${BASE_URL}/api/v1/likes/${productId}`,
        null,
        { headers }
    );
    const ok = check(res, {
        'like create 2xx or 409': (r) => r.status >= 200 && r.status < 300 || r.status === 409,
    });
    const created = res.status >= 200 && res.status < 300;
    likeSuccessRate.add(ok ? 1 : 0);
    likeResponseTime.add(res.timings.duration);
    if (created) likeEventsSent.add(1);
}
