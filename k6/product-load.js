import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 동시 부하 테스트 (목록 + 상세 혼합)
 *
 * 단계별 부하 증가:
 * - Ramp-up: 0→20 VU (30초)
 * - Sustain: 20 VU 유지 (1분)
 * - Ramp-down: 20→0 VU (10초)
 *
 * 실행 방법:
 *   k6 run k6/product-load.js
 */

const BASE_URL = 'http://localhost:8080';
const SORT_TYPES = ['LATEST', 'PRICE_ASC', 'PRICE_DESC', 'LIKES_DESC'];
const BRAND_IDS = [1, 5, 20, 50, 100];
const PRODUCT_COUNT = 200000;

export const options = {
  stages: [
    { duration: '30s', target: 20 },  // 0→20 VU
    { duration: '1m', target: 20 },   // 20 VU 유지
    { duration: '10s', target: 0 },   // 정리
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    http_req_failed: ['rate<0.05'],
  },
};

export default function () {
  const r = Math.random();

  if (r < 0.5) {
    // 50%: 상품 목록 전체 조회
    const sort = SORT_TYPES[Math.floor(Math.random() * SORT_TYPES.length)];
    const url = `${BASE_URL}/api/v1/products?sort=${sort}&size=20`;
    const res = http.get(url, { tags: { name: 'list_all' } });
    check(res, { '200 OK': (r) => r.status === 200 });

  } else if (r < 0.7) {
    // 20%: 상품 목록 브랜드 필터
    const sort = SORT_TYPES[Math.floor(Math.random() * SORT_TYPES.length)];
    const brandId = BRAND_IDS[Math.floor(Math.random() * BRAND_IDS.length)];
    const url = `${BASE_URL}/api/v1/products?sort=${sort}&size=20&brandId=${brandId}`;
    const res = http.get(url, { tags: { name: 'list_brand' } });
    check(res, { '200 OK': (r) => r.status === 200 });

  } else {
    // 30%: 상품 상세
    const productId = Math.random() < 0.8
      ? Math.floor(Math.random() * 1000) + 1
      : Math.floor(Math.random() * PRODUCT_COUNT) + 1;
    const url = `${BASE_URL}/api/v1/products/${productId}`;
    const res = http.get(url, { tags: { name: 'detail' } });
    check(res, { '200 OK': (r) => r.status === 200 });
  }

  sleep(0.3);
}
