/**
 * 상품 목록(PLP) 부하 테스트 — GET /api/v1/products
 *
 * - README(performance) 기준:
 *   - 블랙프라이데이 목표: TPS 10,000+
 *   - SLO: p95 ≤ 200ms, p99 ≤ 400ms, 에러율 < 1%
 * - 사용 예:
 *   - 단일 실행:   k6 run docs/load-test/k6-product-plp.js
 *   - 옵션 지정:   k6 run --vus 50 --duration 60s docs/load-test/k6-product-plp.js
 *   - 정렬 고정:   k6 run -e SORT=likes_desc docs/load-test/k6-product-plp.js
 *   - 페이지 고정: k6 run -e PAGE=50 docs/load-test/k6-product-plp.js
 */
import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

// 정렬: latest | price_asc | price_desc | likes_desc
const SORTS = ["latest", "price_asc", "price_desc", "likes_desc"];
const SORT =
  __ENV.SORT && SORTS.includes(__ENV.SORT)
    ? __ENV.SORT
    : SORTS[Math.floor(Math.random() * SORTS.length)];

// 페이지: 미지정 시 0~2 중 랜덤 (딥 페이징은 PAGE=50 등 별도 시나리오에서 측정)
const PAGE =
  __ENV.PAGE !== undefined ? parseInt(__ENV.PAGE, 10) : Math.floor(Math.random() * 3);

export const options = {
  vus: 50,
  duration: "60s",
  thresholds: {
    // 성능 README의 SLO: p95 ≤ 200ms, p99 ≤ 400ms
    http_req_duration: ["p(95)<200", "p(99)<400"],
    http_req_failed: ["rate<0.01"],
  },
};

export default function () {
  const url = `${BASE_URL}/api/v1/products?sort=${SORT}&page=${PAGE}&size=20`;
  const res = http.get(url);
  check(res, {
    "status is 200": (r) => r.status === 200,
  });
  // 혼합 트래픽 시나리오 가정을 위해 약간의 think time
  sleep(0.5);
}
