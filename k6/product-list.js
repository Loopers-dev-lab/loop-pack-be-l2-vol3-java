import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 상품 목록 조회 성능 테스트
 *
 * 테스트 시나리오:
 * 1. 전체 조회 (brandId 없음) - 4종 정렬
 * 2. 브랜드 필터 조회 (brandId 있음) - 4종 정렬
 *
 * 실행 방법:
 *   k6 run k6/product-list.js
 *   k6 run --duration 30s --vus 10 k6/product-list.js
 */

const BASE_URL = 'http://localhost:8080';
const SORT_TYPES = ['LATEST', 'PRICE_ASC', 'PRICE_DESC', 'LIKES_DESC'];
const BRAND_IDS = [1, 5, 20, 50, 100]; // 다양한 브랜드

export const options = {
  // Stage 1: 단일 유저 기본 성능 측정
  scenarios: {
    baseline: {
      executor: 'constant-vus',
      vus: 1,
      duration: '30s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'], // P95 < 500ms, P99 < 1s
    http_req_failed: ['rate<0.01'],                  // 에러율 1% 미만
  },
};

export default function () {
  const sortType = SORT_TYPES[Math.floor(Math.random() * SORT_TYPES.length)];

  // 70%: 전체 조회, 30%: 브랜드 필터
  if (Math.random() < 0.7) {
    productListAll(sortType);
  } else {
    const brandId = BRAND_IDS[Math.floor(Math.random() * BRAND_IDS.length)];
    productListByBrand(sortType, brandId);
  }

  sleep(0.5); // 요청 간 간격
}

function productListAll(sortType) {
  const url = `${BASE_URL}/api/v1/products?sort=${sortType}&size=20`;
  const res = http.get(url, { tags: { name: 'product_list_all' } });

  check(res, {
    '200 OK': (r) => r.status === 200,
    'has products': (r) => JSON.parse(r.body).data.products.length > 0,
  });
}

function productListByBrand(sortType, brandId) {
  const url = `${BASE_URL}/api/v1/products?sort=${sortType}&size=20&brandId=${brandId}`;
  const res = http.get(url, { tags: { name: 'product_list_brand' } });

  check(res, {
    '200 OK': (r) => r.status === 200,
  });
}
