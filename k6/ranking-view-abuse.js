// View 이벤트 어뷰징 영향 실측 테스트
// 시나리오:
//   - 중복 방지 없음(A) 상태에서 동일 사용자/봇이 같은 상품을 반복 조회
//   - 단일 사용자의 반복 조회만으로 랭킹 점수가 얼마나 증가하는지 측정
//   - 다중 계정(봇 farm) 시뮬레이션
//
// 실행:
//   k6 run ranking-view-abuse.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

const errorRate = new Rate('error_rate');
const viewRequests = new Counter('view_requests');
const viewDuration = new Trend('view_duration', true);

export const options = {
  scenarios: {
    // 시나리오 1: 단일 봇이 1상품을 반복 조회 (어뷰징 극단 사례)
    single_bot_abuse: {
      executor: 'constant-arrival-rate',
      rate: 100,                // 초당 100회 조회
      timeUnit: '1s',
      duration: '30s',          // 30초 동안 = 3000회 조회
      preAllocatedVUs: 20,
      maxVUs: 50,
      exec: 'singleBotAbuse',
      tags: { scenario: 'single_bot' },
    },
    // 시나리오 2: 10개 봇 계정이 같은 상품을 조회 (분산 어뷰징)
    bot_farm_abuse: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 20,
      maxVUs: 50,
      exec: 'botFarmAbuse',
      startTime: '35s',         // single_bot 완료 후 시작
      tags: { scenario: 'bot_farm' },
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    error_rate: ['rate<0.05'],
  },
};

const BASE_URL = 'http://host.docker.internal:8080';
const TARGET_PRODUCT_ID = 'P001';     // 어뷰저가 공략하는 상품
const BOT_COUNT = 10;

export function singleBotAbuse() {
  // 동일 사용자(익명/동일 세션)가 반복 조회
  const res = http.get(`${BASE_URL}/api/v1/products/${TARGET_PRODUCT_ID}`, {
    tags: { endpoint: 'product_detail', abuser: 'single' },
  });

  viewRequests.add(1);
  viewDuration.add(res.timings.duration);
  errorRate.add(res.status >= 400);

  check(res, {
    'product detail status 200': (r) => r.status === 200,
  });
}

export function botFarmAbuse() {
  // 10개 봇 계정이 번갈아 조회 (분산 어뷰징)
  const botId = Math.floor(Math.random() * BOT_COUNT) + 1;
  const res = http.get(`${BASE_URL}/api/v1/products/${TARGET_PRODUCT_ID}`, {
    headers: { 'X-Bot-Id': 'bot-' + botId },
    tags: { endpoint: 'product_detail', abuser: 'bot_farm', bot_id: botId },
  });

  viewRequests.add(1);
  viewDuration.add(res.timings.duration);
  errorRate.add(res.status >= 400);

  check(res, {
    'product detail status 200': (r) => r.status === 200,
  });
}

// 테스트 후 결과 요약:
// 예상 결과 (중복 방지 없음 = 선택 5-2의 A):
//   - single_bot_abuse: 3000회 조회 → score += 0.1 * 3000 = 300점
//   - bot_farm_abuse:   3000회 조회 → score += 0.1 * 3000 = 300점
//   - 총 600점을 단일 상품에 부여 가능
//   - 좋아요 3000건(실제 사용자 3000명)에 필적하는 점수를 봇 1개로 확보
//
// Bitmap(D-3) 도입 시:
//   - single_bot_abuse: 1 유저 → score += 0.1 * 1 = 0.1점
//   - bot_farm_abuse: 10 봇 → score += 0.1 * 10 = 1.0점
//   - 총 1.1점 (600배 축소)
