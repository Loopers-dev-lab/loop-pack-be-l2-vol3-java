import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * DB 보호 효과 테스트 — 캐시 워밍업 후 20 VU 부하
 *
 * 캐시가 워밍업된 상태에서 동일 키를 반복 조회.
 * 대부분 캐시 히트 → DB 호출 최소화 → 응답 시간 안정성 확인.
 *
 * 실행 방법:
 *   k6 run k6/cache-protection.js
 */

const BASE_URL = 'http://localhost:8080';
const SORT_TYPES = ['LATEST', 'PRICE_ASC', 'PRICE_DESC', 'LIKES_DESC'];

export const options = {
  stages: [
    { duration: '10s', target: 20 },  // 0→20 VU
    { duration: '30s', target: 20 },  // 20 VU 유지
    { duration: '5s', target: 0 },    // 정리
  ],
  thresholds: {
    http_req_duration: ['p(95)<100', 'p(99)<200'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  // 캐시 히트가 대부분인 시나리오: 첫 페이지만 반복 조회
  const sort = SORT_TYPES[Math.floor(Math.random() * SORT_TYPES.length)];
  const url = `${BASE_URL}/api/v1/products?sort=${sort}&size=20`;
  const res = http.get(url, { tags: { name: 'list_cached' } });

  check(res, {
    '200 OK': (r) => r.status === 200,
  });

  sleep(0.1);
}
