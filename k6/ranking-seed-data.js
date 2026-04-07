// 랭킹 부하 테스트를 위한 대량 데이터 Seed
//
// 사전 조건:
//   - commerce-api 서버 실행 중
//   - Redis 연결 가능
//
// 실행:
//   k6 run ranking-seed-data.js
//
// 설명:
//   k6에서 직접 Redis에 ZADD는 불가하므로,
//   상품 조회 API를 대량 호출하여 View 이벤트를 발생시키고
//   ZSET에 점수를 누적합니다.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const seedCount = new Counter('seed_count');

export const options = {
  scenarios: {
    seed_views: {
      executor: 'per-vu-iterations',
      vus: 10,
      iterations: 500,
      exec: 'seedViewEvents',
    },
  },
};

const PRODUCT_IDS = ['k6prod01', 'k6prod02', 'k6prod03', 'k6prod04', 'k6prod05'];

export function seedViewEvents() {
  const productId = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
  const memberId = __VU * 10000 + __ITER;

  const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
    headers: { 'X-USER-ID': String(memberId) },
  });

  check(res, { 'seed status 200': (r) => r.status === 200 });
  seedCount.add(1);
  sleep(0.01);
}

export function handleSummary(data) {
  const count = data.metrics.seed_count ? data.metrics.seed_count.values.count : 0;
  console.log(`\n=== Seed 완료: ${count}건의 View 이벤트 발행 ===\n`);
  console.log('각 유저-상품 조합은 Bitmap으로 1회만 카운트됩니다.');
  console.log('실제 반영된 이벤트 수는 유니크(유저×상품) 조합 수와 같습니다.');
  return {};
}
