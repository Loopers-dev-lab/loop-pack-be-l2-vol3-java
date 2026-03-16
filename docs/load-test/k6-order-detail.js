/**
 * 주문 상세 부하 테스트 — GET /api/v1/orders/{id} (인증 필요)
 *
 * - README(performance) 기준:
 *   - 블랙프라이데이 목표: TPS 1,000+ (주문 목록/상세 합산)
 *   - SLO: p95 ≤ 300ms, p99 ≤ 600ms, 에러율 < 1%
 * - 사전:
 *   - `local-auth-setup.sh` 로 LOGIN_ID에 해당하는 유저를 생성.
 *   - 해당 유저가 가진 주문 ID 범위를 대략 파악해 MIN_ORDER_ID / MAX_ORDER_ID 를 설정.
 *
 * 사용 예:
 *   k6 run -e LOGIN_ID=perfuser -e MIN_ORDER_ID=1 -e MAX_ORDER_ID=100 \
 *     docs/load-test/k6-order-detail.js
 */
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const LOGIN_ID = __ENV.LOGIN_ID || "perfuser";
const MIN_ORDER_ID = parseInt(__ENV.MIN_ORDER_ID || "1", 10);
const MAX_ORDER_ID = parseInt(__ENV.MAX_ORDER_ID || "100", 10);

export const options = {
  vus: 50,
  duration: "30s",
  thresholds: {
    http_req_duration: ["p(95)<300", "p(99)<600"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const id =
    MIN_ORDER_ID +
    Math.floor(Math.random() * Math.max(1, MAX_ORDER_ID - MIN_ORDER_ID + 1));

  const url = `${BASE_URL}/api/v1/orders/${id}`;
  const params = {
    headers: {
      "X-Loopers-LoginId": LOGIN_ID,
    },
  };

  const res = http.get(url, params);

  check(res, {
    "status 200 or 404": (r) => r.status === 200 || r.status === 404,
  });

  // 주문 상세 재조회 패턴을 고려한 짧은 think time
  sleep(0.3);
}

