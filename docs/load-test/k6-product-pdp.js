/**
 * 상품 상세(PDP) 부하 테스트 — GET /api/v1/products/{id}
 *
 * - README(performance) 기준:
 *   - 블랙프라이데이 목표: TPS 7,000+
 *   - SLO: p95 ≤ 200ms, p99 ≤ 400ms, 에러율 < 1%
 * - 사용 예:
 *   - k6 run docs/load-test/k6-product-pdp.js
 *   - k6 run --vus 100 --duration 60s -e MAX_PRODUCT_ID=500000 docs/load-test/k6-product-pdp.js
 */
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

// 시드 후 존재하는 상품 ID 범위 (규모별 시드에 맞게 조정: 1 ~ SCALE)
const MIN_ID = parseInt(__ENV.MIN_PRODUCT_ID || "1", 10);
const MAX_ID = parseInt(__ENV.MAX_PRODUCT_ID || "100000", 10);

if (Number.isNaN(MIN_ID) || Number.isNaN(MAX_ID)) {
  throw new Error(
    `MIN_PRODUCT_ID/MAX_PRODUCT_ID must be numbers. got MIN=${MIN_ID}, MAX=${MAX_ID}`,
  );
}
if (MIN_ID > MAX_ID) {
  throw new Error(
    `MIN_PRODUCT_ID must be <= MAX_PRODUCT_ID. got MIN=${MIN_ID}, MAX=${MAX_ID}`,
  );
}

export const options = {
  vus: 100,
  duration: "60s",
  thresholds: {
    http_req_duration: ["p(95)<200", "p(99)<400"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const range = MAX_ID - MIN_ID + 1;
  const id = MIN_ID + Math.floor(Math.random() * range);
  const url = `${BASE_URL}/api/v1/products/${id}`;
  const res = http.get(url);
  check(res, {
    "status 200 or 404": (r) => r.status === 200 || r.status === 404,
  });
  // PDP는 PLP보다 짧은 think time 가정
  sleep(0.3);
}
