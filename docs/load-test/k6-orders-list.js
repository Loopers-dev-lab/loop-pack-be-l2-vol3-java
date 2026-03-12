/**
 * 주문 목록 부하 테스트 — GET /api/v1/orders (인증 필요)
 *
 * - README(performance) 기준:
 *   - 블랙프라이데이 목표: TPS 1,000+
 *   - SLO: p95 ≤ 300ms, p99 ≤ 600ms, 에러율 < 1%
 * - 사전: `local-auth-setup.sh` 로 perfuser 등 테스트 유저를 생성해 둔다.
 * - 사용 예:
 *   - k6 run -e LOGIN_ID=perfuser docs/load-test/k6-orders-list.js
 *   - k6 run --vus 50 --duration 30s -e BASE_URL=http://localhost:8081 docs/load-test/k6-orders-list.js
 */
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const LOGIN_ID = __ENV.LOGIN_ID || "perfuser";

export const options = {
  vus: 50,
  duration: "30s",
  thresholds: {
    http_req_duration: ["p(95)<300", "p(99)<600"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const start = "2020-01-01T00:00:00Z";
  const end = "2030-12-31T23:59:59Z";
  const url = `${BASE_URL}/api/v1/orders?start=${start}&end=${end}&page=0&size=20`;
  const params = { headers: { "X-Loopers-LoginId": LOGIN_ID } };
  const res = http.get(url, params);
  check(res, { "status 200": (r) => r.status === 200 });
  sleep(0.5);
}
