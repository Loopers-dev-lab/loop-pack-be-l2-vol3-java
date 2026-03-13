/**
 * 좋아요 등록/취소 쓰기 부하 테스트 — POST/DELETE /api/v1/likes
 *
 * - README(performance) 기준:
 *   - 블랙프라이데이 목표: TPS 3,000+
 *   - 쓰기 API로서 DB/Redis 쓰기 경합, Row-level Lock, Redis CPU Overload 등을 관찰하는 것이 목적.
 * - 시나리오:
 *   - VU마다 무작위 상품 ID를 골라, 50% 확률로 POST(등록), 50% 확률로 DELETE(취소)를 호출.
 *   - 특정 인기 상품군에 트래픽을 집중시키고 싶다면 PRODUCT_ID_RANGE 를 좁게 설정.
 *
 * 사용 예:
 *   k6 run -e LOGIN_ID=perfuser -e MIN_PRODUCT_ID=1 -e MAX_PRODUCT_ID=100000 \
 *     docs/load-test/k6-likes-write.js
 */
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const LOGIN_ID = __ENV.LOGIN_ID || "perfuser";
const MIN_PRODUCT_ID = parseInt(__ENV.MIN_PRODUCT_ID || "1", 10);
const MAX_PRODUCT_ID = parseInt(__ENV.MAX_PRODUCT_ID || "100000", 10);

export const options = {
  vus: 100,
  duration: "60s",
  thresholds: {
    // 쓰기 API는 네트워크/DB/Redis를 모두 거치므로 주문과 동일 수준의 SLO로 시작
    http_req_duration: ["p(95)<300", "p(99)<600"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const productId =
    MIN_PRODUCT_ID +
    Math.floor(Math.random() * Math.max(1, MAX_PRODUCT_ID - MIN_PRODUCT_ID + 1));

  const headers = {
    "Content-Type": "application/json",
    "X-Loopers-LoginId": LOGIN_ID,
  };

  const doLike = Math.random() < 0.5;

  if (doLike) {
    // 좋아요 등록: POST /api/v1/likes
    const payload = JSON.stringify({ productId });
    const res = http.post(`${BASE_URL}/api/v1/likes`, payload, { headers });
    check(res, {
      "like POST 201 or 409": (r) =>
        r.status === 201 || r.status === 409 || r.status === 400,
    });
  } else {
    // 좋아요 취소: DELETE /api/v1/likes/{productId}
    const res = http.del(`${BASE_URL}/api/v1/likes/${productId}`, null, {
      headers,
    });
    check(res, {
      "like DELETE 204 or 404": (r) =>
        r.status === 204 || r.status === 404 || r.status === 400,
    });
  }

  // 쓰기 집중 시나리오이지만, 약간의 think time을 둔다
  sleep(0.1);
}

