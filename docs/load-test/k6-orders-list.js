/**
 * 주문 목록 부하 테스트 — GET /api/v1/orders (인증 필요)
 *
 * - README(performance) 기준:
 *   - 블랙프라이데이 목표: TPS 1,000+
 *   - SLO: p95 ≤ 300ms, p99 ≤ 600ms, 에러율 < 1%
 * - 사전: 반드시 `local-auth-setup.sh` 로 perfuser 등 테스트 유저를 생성해 둔다.
 *   유저가 없으면 GET /api/v1/orders 는 404(사용자를 찾을 수 없습니다)를 반환하므로
 *   status 200 체크 실패·에러율 100%가 된다. latency는 서버 응답 기준으로 수집된다.
 * - 기본 조회 구간:
 *   - 기간: 2020-01-01T00:00:00Z ~ 2030-12-31T23:59:59Z (ENV START/END 로 오버라이드 가능)
 *   - 페이징: page=0, size=20 (ENV PAGE/SIZE 로 오버라이드 가능)
 * - 에러 허용 옵션:
 *   - 기본적으로 status 200만 성공으로 간주한다.
 *   - 초기 개발 단계 등에서 4xx/5xx 응답을 허용하고 싶다면 ALLOW_NON_200=true 로 실행한다.
 * - 사용 예:
 *   - k6 run -e LOGIN_ID=perfuser docs/load-test/k6-orders-list.js
 *   - k6 run --vus 50 --duration 30s -e BASE_URL=http://localhost:8081 docs/load-test/k6-orders-list.js
 *   - k6 run -e LOGIN_ID=perfuser -e PAGE=50 -e SIZE=20 docs/load-test/k6-orders-list.js
 */
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const LOGIN_ID = __ENV.LOGIN_ID || "perfuser";
const START =
  __ENV.START || "2020-01-01T00:00:00Z";
const END =
  __ENV.END || "2030-12-31T23:59:59Z";
const PAGE =
  __ENV.PAGE !== undefined ? parseInt(__ENV.PAGE, 10) : 0;
const SIZE =
  __ENV.SIZE !== undefined ? parseInt(__ENV.SIZE, 10) : 20;
const ALLOW_NON_200 =
  (__ENV.ALLOW_NON_200 || "false").toLowerCase() === "true";

export const options = {
  vus: 50,
  duration: "30s",
  thresholds: {
    http_req_duration: ["p(95)<300", "p(99)<600"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const url = `${BASE_URL}/api/v1/orders?start=${START}&end=${END}&page=${PAGE}&size=${SIZE}`;
  const params = { headers: { "X-Loopers-LoginId": LOGIN_ID } };
  const res = http.get(url, params);
  check(res, {
    "status 200 (or allowed non-200)": (r) =>
      ALLOW_NON_200 ? true : r.status === 200,
  });
  sleep(0.5);
}
