import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * Step 3 — 핵심! 선착순 쿠폰 수량 정확성 테스트
 *
 * 100장 한정 쿠폰에 500 VU가 동시 요청 → 정확히 100장만 발급되는지 확인.
 * Thin Producer (Redis SETNX + DECR + Kafka) → Consumer (DB CAS) 전체 흐름 검증.
 *
 * 관찰 포인트:
 *   - coupons.issued_count == 100 (정확)
 *   - user_coupons 건수 == 100
 *   - coupon_issue_result ISSUED == 100, REJECTED == 나머지
 *   - 초과 발급 0건
 *
 * 실행:
 *   RUSH_COUPON_ID=1  # seed-session7.sh 출력값
 *   docker run --rm -i --network host \
 *     -e RUSH_COUPON_ID=$RUSH_COUPON_ID \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/session7/rush-coupon-spike.js
 */

const RUSH_COUPON_ID = __ENV.RUSH_COUPON_ID || '1';

export const options = {
    scenarios: {
        rush: {
            executor: 'shared-iterations',
            vus: 500,
            iterations: 500,
            maxDuration: '30s',
        },
    },
    thresholds: {
        'rush_request_duration': ['p(95)<100'],
    },
};

const BASE = 'http://localhost:8080';

const rushSuccess = new Counter('rush_accepted');     // 202 응답
const rushConflict = new Counter('rush_conflict');    // 409 중복
const rushSoldOut = new Counter('rush_sold_out');     // 400 수량 소진
const rushError = new Counter('rush_error');          // 기타 에러
const rushDuration = new Trend('rush_request_duration');

export default function () {
    // 각 VU = 각 유저 (1:1 매핑, 중복 요청 없음)
    const userId = __VU;
    const HEADERS = authHeaders(`k6user${userId}`, 'Test1234!');

    const start = Date.now();
    const res = http.post(
        `${BASE}/api/v1/coupons/${RUSH_COUPON_ID}/rush-issue`,
        null,
        { headers: HEADERS }
    );
    rushDuration.add(Date.now() - start);

    if (res.status === 202) {
        rushSuccess.add(1);
    } else if (res.status === 409) {
        rushConflict.add(1);
    } else if (res.status === 400) {
        rushSoldOut.add(1);
    } else {
        rushError.add(1);
    }
}

export function handleSummary(data) {
    const accepted = data.metrics.rush_accepted ? data.metrics.rush_accepted.values.count : 0;
    const conflict = data.metrics.rush_conflict ? data.metrics.rush_conflict.values.count : 0;
    const soldOut = data.metrics.rush_sold_out ? data.metrics.rush_sold_out.values.count : 0;
    const error = data.metrics.rush_error ? data.metrics.rush_error.values.count : 0;
    const p95 = data.metrics.rush_request_duration
        ? data.metrics.rush_request_duration.values['p(95)'].toFixed(1) : '?';

    return {
        stdout: `
=== 선착순 쿠폰 테스트 결과 ===
  202 Accepted (Kafka 진입): ${accepted}
  409 Conflict (중복 요청):  ${conflict}
  400 Sold Out (수량 소진):  ${soldOut}
  에러:                      ${error}
  요청 p95:                  ${p95}ms

  → DB 검증 필요:
    SELECT issued_count, max_quantity FROM coupons WHERE coupon_id = ${RUSH_COUPON_ID};
    SELECT COUNT(*) FROM user_coupons WHERE coupon_id = ${RUSH_COUPON_ID};
    SELECT status, COUNT(*) FROM coupon_issue_result GROUP BY status;
`,
    };
}
