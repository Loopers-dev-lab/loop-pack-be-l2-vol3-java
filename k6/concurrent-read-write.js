import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

/**
 * 동시 읽기+쓰기 테스트
 *
 * 시나리오:
 * - 19 VU: 상품 목록/상세 반복 조회 (읽기)
 * - 1 VU: 10초마다 상품 가격 수정 (쓰기 → 캐시 무효화)
 *
 * 검증 포인트:
 * - 쓰기 발생 후에도 읽기 응답이 안정적인가
 * - 캐시 무효화 후 첫 읽기(miss)의 레이턴시 스파이크는 얼마인가
 */

const BASE_URL = 'http://localhost:8080';
const SORT_TYPES = ['LATEST', 'PRICE_ASC', 'PRICE_DESC', 'LIKES_DESC'];
const ADMIN_HEADER = { 'Content-Type': 'application/json', 'X-Loopers-Ldap': 'loopers.admin' };

const stalePriceCount = new Counter('stale_price_detected');

export const options = {
  scenarios: {
    readers: {
      executor: 'constant-vus',
      vus: 19,
      duration: '30s',
      exec: 'reader',
    },
    writer: {
      executor: 'constant-vus',
      vus: 1,
      duration: '30s',
      exec: 'writer',
      startTime: '5s', // 5초 후 쓰기 시작 (캐시 워밍업 시간)
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.05'],
  },
};

// 워밍업: 캐시 저장
export function setup() {
  for (const sort of SORT_TYPES) {
    http.get(`${BASE_URL}/api/v1/products?sort=${sort}&size=20`);
  }
  // 상품 1의 원래 가격 저장
  const res = http.get(`${BASE_URL}/api/v1/products/1`);
  const data = JSON.parse(res.body);
  return { originalPrice: data.data.basePrice };
}

export function reader() {
  const r = Math.random();
  if (r < 0.7) {
    const sort = SORT_TYPES[Math.floor(Math.random() * SORT_TYPES.length)];
    const res = http.get(`${BASE_URL}/api/v1/products?sort=${sort}&size=20`,
      { tags: { name: 'read_list' } });
    check(res, { '200 OK': (r) => r.status === 200 });
  } else {
    const res = http.get(`${BASE_URL}/api/v1/products/1`,
      { tags: { name: 'read_detail' } });
    check(res, { '200 OK': (r) => r.status === 200 });
  }
  sleep(0.1);
}

export function writer() {
  // 10초마다 가격 변경
  const newPrice = 10000 + Math.floor(Math.random() * 90000);
  const res = http.patch(`${BASE_URL}/api-admin/v1/products/1`,
    JSON.stringify({ basePrice: newPrice }),
    { headers: ADMIN_HEADER, tags: { name: 'write_update' } }
  );
  check(res, { 'update 200': (r) => r.status === 200 });
  sleep(10);
}

// 테스트 종료 후 원래 가격 복원
export function teardown(data) {
  http.patch(`${BASE_URL}/api-admin/v1/products/1`,
    JSON.stringify({ basePrice: data.originalPrice }),
    { headers: ADMIN_HEADER }
  );
}
