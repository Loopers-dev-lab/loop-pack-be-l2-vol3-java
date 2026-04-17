/**
 * 시나리오 4: 혼합 부하 (파이프라인 E2E)
 *
 * 목적:
 *   VIEW 이벤트 발생 → ranking_metrics 반영 → 스케줄러 ZADD → 랭킹 API 조회까지
 *   end-to-end 지연 실측.
 *
 * 전제:
 *   - commerce-api, commerce-streamer 모두 실행 중이어야 함
 *   - DB에 상품이 존재해야 함 (PRODUCT_COUNT 범위 내 ID)
 *
 * E2E 지연 측정 방법:
 *   1. 테스트 시작 직전 랭킹 API에서 특정 상품 score를 기록
 *   2. VIEW 이벤트 발생 후 랭킹 API를 폴링하여 score 변화 감지 시각 기록
 *   3. 차이 = 파이프라인 end-to-end 지연 (목표: 5초 이내)
 *   → 이 스크립트는 처리량/레이턴시를 측정. E2E 지연은 로그에서 수동 확인 권장.
 *
 * 실행:
 *   k6 run k6/scenario4-mixed-load.js
 *   k6 run k6/scenario4-mixed-load.js -e VIEW_RATE=100 -e RANKING_RATE=100
 *
 * 환경 변수:
 *   VIEW_RATE     : VIEW 이벤트 초당 목표 RPS (기본: 50)
 *   RANKING_RATE  : 랭킹 조회 초당 목표 RPS (기본: 50)
 *   DURATION      : 테스트 지속 시간 (기본: 120s)
 *   PRODUCT_COUNT : 랜덤 상품 수 (기본: 10)
 */

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL     = __ENV.BASE_URL    || 'http://localhost:8080';
const VIEW_RATE    = parseInt(__ENV.VIEW_RATE    || '50');
const RANKING_RATE = parseInt(__ENV.RANKING_RATE || '50');
const DURATION     = __ENV.DURATION    || '120s';

// ID 6 미존재 — DB에 실재하는 상품 ID만 명시
const PRODUCT_IDS = (__ENV.PRODUCT_IDS || '1,2,3,4,5,7,8,9,10')
    .split(',').map(id => parseInt(id.trim()));

const viewResponseTime    = new Trend('view_response_time_ms', true);
const rankingResponseTime = new Trend('ranking_response_time_ms', true);
const viewSuccessRate     = new Rate('view_success_rate');
const rankingSuccessRate  = new Rate('ranking_success_rate');

export const options = {
  scenarios: {
    view_traffic: {
      executor: 'constant-arrival-rate',
      rate: VIEW_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.ceil(VIEW_RATE * 1.2),
      exec: 'viewProduct',
    },
    ranking_read: {
      executor: 'constant-arrival-rate',
      rate: RANKING_RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.ceil(RANKING_RATE * 1.2),
      exec: 'readRanking',
    },
  },
  thresholds: {
    'view_success_rate':      ['rate>0.99'],
    'ranking_success_rate':   ['rate>0.999'],
    'view_response_time_ms':  ['p(99)<500'],
    'ranking_response_time_ms': ['p(99)<100'],
    'http_req_failed':        ['rate<0.01'],
  },
};

export function viewProduct() {
  const productId = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];
  const res = http.get(`${BASE_URL}/api/v1/products/${productId}`);

  const ok = check(res, { 'view status 200': (r) => r.status === 200 });
  viewSuccessRate.add(ok ? 1 : 0);
  viewResponseTime.add(res.timings.duration);
}

export function readRanking() {
  const res = http.get(`${BASE_URL}/api/v1/rankings?size=20`);

  const ok = check(res, {
    'ranking status 200': (r) => r.status === 200,
    'ranking meta SUCCESS': (r) => JSON.parse(r.body).meta?.result === 'SUCCESS',
  });
  rankingSuccessRate.add(ok ? 1 : 0);
  rankingResponseTime.add(res.timings.duration);
}
