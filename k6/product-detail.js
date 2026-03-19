import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 상품 상세 조회 성능 테스트 (캐시 효과 측정용)
 *
 * 시나리오:
 * - 인기 상품(1~1000)에 80% 집중, 나머지 20%는 전체 범위
 * - 같은 상품 반복 조회 → 캐시 히트율 측정
 *
 * 실행 방법:
 *   k6 run k6/product-detail.js
 *   k6 run --duration 30s --vus 10 k6/product-detail.js
 */

const BASE_URL = 'http://localhost:8080';
const PRODUCT_COUNT = 200000;

export const options = {
  scenarios: {
    baseline: {
      executor: 'constant-vus',
      vus: 1,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const productId = generateProductId();
  const url = `${BASE_URL}/api/v1/products/${productId}`;
  const res = http.get(url, { tags: { name: 'product_detail' } });

  check(res, {
    '200 OK': (r) => r.status === 200,
  });

  sleep(0.3);
}

/** 인기 상품에 80% 집중 (캐시 히트율 테스트) */
function generateProductId() {
  if (Math.random() < 0.8) {
    return Math.floor(Math.random() * 1000) + 1;    // 1~1000
  }
  return Math.floor(Math.random() * PRODUCT_COUNT) + 1; // 1~200000
}
