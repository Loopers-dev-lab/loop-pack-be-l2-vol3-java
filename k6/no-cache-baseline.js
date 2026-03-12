import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * DB Only 부하 테스트 (캐시 우회)
 *
 * 2페이지 요청(cursor 있음) → 캐시를 우회하고 매번 DB 조회.
 * 캐시 히트 테스트와 동일 부하(20 VU)로 비교.
 *
 * 실행 방법:
 *   k6 run k6/no-cache-baseline.js
 */

const BASE_URL = 'http://localhost:8080';
const SORT_TYPES = ['LATEST', 'PRICE_ASC', 'PRICE_DESC', 'LIKES_DESC'];

export const options = {
  stages: [
    { duration: '10s', target: 20 },
    { duration: '30s', target: 20 },
    { duration: '5s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

// 미리 가져올 커서 — 2페이지 요청을 시뮬레이션
let cursors = {};

export function setup() {
  // 각 sort별 첫 페이지를 조회해서 nextCursor 확보
  const result = {};
  for (const sort of SORT_TYPES) {
    const res = http.get(`${BASE_URL}/api/v1/products?sort=${sort}&size=20`);
    if (res.status === 200) {
      const body = JSON.parse(res.body);
      // 응답에 cursor가 있으면 저장
      if (body.data && body.data.products && body.data.products.length > 0) {
        const lastProduct = body.data.products[body.data.products.length - 1];
        result[sort] = lastProduct.id;
      }
    }
  }
  return result;
}

export default function (data) {
  const sort = SORT_TYPES[Math.floor(Math.random() * SORT_TYPES.length)];
  // 2페이지 요청 → cursor 파라미터 포함 → 캐시 우회 (첫 페이지만 캐싱)
  const lastId = data[sort] || 100000;
  const url = `${BASE_URL}/api/v1/products?sort=${sort}&size=20&cursorId=${lastId}`;
  const res = http.get(url, { tags: { name: 'list_no_cache' } });

  check(res, {
    '200 OK': (r) => r.status === 200,
  });

  sleep(0.1);
}
