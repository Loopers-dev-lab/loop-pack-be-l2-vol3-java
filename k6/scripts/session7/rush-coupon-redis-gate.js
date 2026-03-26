import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * Step 3 — Redis DECR 게이트키퍼 효과 측정
 *
 * 1000명이 동시 요청 → Redis DECR이 ~100건만 Kafka에 진입시키고
 * ~900건은 Producer 측에서 즉시 거절하는지 확인한다.
 *
 * 관찰 포인트:
 *   - 202 (Kafka 진입): ~100건
 *   - 400 (Redis DECR 거절): ~900건
 *   - 응답 시간: 거절 응답이 < 5ms (Redis만 거침)
 *   - Kafka UI에서 coupon-issue-requests 토픽 메시지 수: ~100건
 *   - Redis: GET coupon:{id}:remaining → 0 (±1)
 *
 * 실행:
 *   # 먼저 Redis remaining을 리셋해야 함
 *   # redis-cli SET coupon:{COUPON_ID}:remaining 100
 *
 *   RUSH_COUPON_ID=1
 *   docker run --rm -i --network host \
 *     -e RUSH_COUPON_ID=$RUSH_COUPON_ID \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/session7/rush-coupon-redis-gate.js
 */

const RUSH_COUPON_ID = __ENV.RUSH_COUPON_ID || '1';

export const options = {
    scenarios: {
        gate_test: {
            executor: 'shared-iterations',
            vus: 1000,
            iterations: 1000,
            maxDuration: '15s',
        },
    },
};

const BASE = 'http://localhost:8080';

const gateAccepted = new Counter('gate_accepted');   // 202 (Kafka 진입)
const gateSoldOut = new Counter('gate_sold_out');     // 400 (Redis DECR 거절)
const gateConflict = new Counter('gate_conflict');   // 409 (SETNX 거절)
const gateError = new Counter('gate_error');
const gateDuration = new Trend('gate_duration');
const gateSoldOutDuration = new Trend('gate_sold_out_duration');

export default function () {
    const userId = __VU;
    const HEADERS = authHeaders(`k6user${userId}`, 'Test1234!');

    const start = Date.now();
    const res = http.post(
        `${BASE}/api/v1/coupons/${RUSH_COUPON_ID}/rush-issue`,
        null,
        { headers: HEADERS }
    );
    const elapsed = Date.now() - start;
    gateDuration.add(elapsed);

    if (res.status === 202) {
        gateAccepted.add(1);
    } else if (res.status === 400) {
        gateSoldOut.add(1);
        gateSoldOutDuration.add(elapsed);  // 거절 응답 시간 별도 측정
    } else if (res.status === 409) {
        gateConflict.add(1);
    } else {
        gateError.add(1);
    }
}

export function handleSummary(data) {
    const accepted = data.metrics.gate_accepted ? data.metrics.gate_accepted.values.count : 0;
    const soldOut = data.metrics.gate_sold_out ? data.metrics.gate_sold_out.values.count : 0;
    const conflict = data.metrics.gate_conflict ? data.metrics.gate_conflict.values.count : 0;
    const error = data.metrics.gate_error ? data.metrics.gate_error.values.count : 0;
    const soldOutP95 = data.metrics.gate_sold_out_duration
        ? data.metrics.gate_sold_out_duration.values['p(95)'].toFixed(1) : '?';

    return {
        stdout: `
=== Redis DECR 게이트키퍼 효과 ===
  202 Accepted (Kafka 진입): ${accepted}   ← 목표: ~100건
  400 Sold Out (Redis 거절): ${soldOut}    ← 목표: ~900건
  409 Conflict (중복 거절):  ${conflict}
  에러:                      ${error}

  거절 응답 p95: ${soldOutP95}ms           ← 목표: < 5ms (Redis만 거침)

  → Kafka UI 확인: coupon-issue-requests 토픽 메시지 수 ≈ ${accepted}건
  → Redis 확인: GET coupon:${RUSH_COUPON_ID}:remaining → 0 (±1)
`,
    };
}
