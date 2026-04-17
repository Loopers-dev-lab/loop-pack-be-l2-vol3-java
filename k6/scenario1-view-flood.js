/**
 * 시나리오 1: VIEW 이벤트 폭주
 *
 * 목적:
 *   상품 조회 → Kafka VIEW 이벤트 → ranking_metrics UPSERT 경로의
 *   DB 병목과 deadlock 여부 확인.
 *
 * 전제:
 *   - DB에 상품이 존재해야 함 (PRODUCT_COUNT 범위 내 ID)
 *   - commerce-api, commerce-streamer 모두 실행 중이어야 함
 *
 * 실행:
 *   k6 run k6/scenario1-view-flood.js
 *   k6 run k6/scenario1-view-flood.js -e PRODUCT_COUNT=20 -e VUS=200
 */

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS      = parseInt(__ENV.VUS      || '100');
const DURATION = __ENV.DURATION || '60s';

// ID 6 미존재 — DB에 실재하는 상품 ID만 명시
const PRODUCT_IDS = (__ENV.PRODUCT_IDS || '1,2,3,4,5,7,8,9,10')
    .split(',').map(id => parseInt(id.trim()));

const responseTime = new Trend('view_response_time_ms', true);
const successRate  = new Rate('view_success_rate');

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    'view_success_rate':      ['rate>0.99'],
    'view_response_time_ms':  ['p(99)<500'],
    'http_req_failed':        ['rate<0.01'],
  },
};

export default function () {
  const productId = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
  const res = http.get(`${BASE_URL}/api/v1/products/${productId}`);

  const ok = check(res, { 'status 200': (r) => r.status === 200 });
  successRate.add(ok ? 1 : 0);
  responseTime.add(res.timings.duration);

  if (!ok) {
    console.warn(`productId=${productId} 조회 실패: ${res.status}`);
  }
}
