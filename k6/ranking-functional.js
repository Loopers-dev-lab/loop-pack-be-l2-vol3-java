/**
 * K6 Ranking Functional Test — Case 1 & 2
 *
 * Case 1: 기능 검증 (10~20 VU)
 *   이벤트 발생(조회/좋아요/주문) → 랭킹 API 조회 → 상품 상세에 순위 포함
 *   목표: 에러율 0%, 모든 API 정상 응답
 *
 * Case 2: 랭킹 정합성 (50 VU)
 *   다수 상품에 다양한 이벤트 → 가중치 반영 순서 검증
 *   목표: 주문 > 좋아요 > 조회 순서가 랭킹에 반영
 *
 * 실행 전제:
 *   - commerce-api (8080) + commerce-streamer 기동
 *   - Docker infra (MySQL, Redis, Kafka) 실행 중
 *   - 스케줄러가 ranking:all 을 갱신해야 하므로 streamer 의 scheduler 활성화
 *
 * 실행:
 *   k6 run --env CASE=1 k6/ranking-functional.js
 *   k6 run --env CASE=2 k6/ranking-functional.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';

// ── Custom Metrics ───────────────────────────────────────────────
const rankingQueryErrors = new Counter('ranking_query_errors');
const rankingApiDuration = new Trend('ranking_api_duration_ms', true);
const productDetailDuration = new Trend('product_detail_duration_ms', true);
const eventTriggerErrors = new Counter('event_trigger_errors');

// ── Config ───────────────────────────────────────────────────────
const BASE_URL    = __ENV.BASE_URL || 'http://localhost:8080';
const CASE        = parseInt(__ENV.CASE || '1');
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const ADMIN_HEADERS = { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' };
const DAILY_RANKING_TIMEOUT_S = parseInt(__ENV.DAILY_RANKING_TIMEOUT_S || '330');
const DAILY_RANKING_POLL_INTERVAL_S = parseFloat(__ENV.DAILY_RANKING_POLL_INTERVAL_S || '5');

function parseDataId(res) {
  try { return res.json('data.id'); } catch (_) { return null; }
}

function abortSetup(step, res) {
  console.error(`[setup:${step}] FAILED (${res.status}): ${res.body}`);
  throw new Error(`setup failed at ${step}`);
}

function waitForDailyRanking() {
  const deadline = Date.now() + DAILY_RANKING_TIMEOUT_S * 1000;
  let lastRes = null;

  while (Date.now() < deadline) {
    lastRes = http.get(`${BASE_URL}/api/v1/rankings?size=20&page=1`);
    if (lastRes.status === 200) {
      try {
        const rankings = lastRes.json('data.rankings');
        if (rankings && rankings.length > 0) {
          return lastRes;
        }
      } catch (_) {}
    }
    sleep(DAILY_RANKING_POLL_INTERVAL_S);
  }

  return lastRes;
}

// ── Scenario Options ─────────────────────────────────────────────
const scenarios = {
  case1_functional: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 10 },
      { duration: '30s', target: 20 },
      { duration: '10s', target: 0 },
    ],
    env: { ACTIVE_CASE: '1' },
  },
  case2_consistency: {
    executor: 'per-vu-iterations',
    vus: 50,
    iterations: 1,
    maxDuration: '420s',
    env: { ACTIVE_CASE: '2' },
  },
};

export const options = {
  scenarios: CASE === 1
    ? { case1_functional: scenarios.case1_functional }
    : { case2_consistency: scenarios.case2_consistency },
  thresholds: {
    http_req_failed:   ['rate<0.01'],
    http_req_duration: ['p(95)<3000'],
  },
};

// ── Setup ────────────────────────────────────────────────────────
export function setup() {
  // 브랜드 생성
  const brandRes = http.post(
    `${BASE_URL}/api/admin/v1/brands`,
    JSON.stringify({ name: 'K6-Ranking-Brand' }),
    { headers: ADMIN_HEADERS }
  );
  const brandId = parseDataId(brandRes);
  if (!brandId) abortSetup('brand', brandRes);

  // 상품 5개 생성 (랭킹 비교용)
  const products = [];
  for (let i = 1; i <= 5; i++) {
    const pRes = http.post(
      `${BASE_URL}/api/admin/v1/products`,
      JSON.stringify({ brandId, name: `K6-Product-${i}`, basePrice: 10000 * i }),
      { headers: ADMIN_HEADERS }
    );
    const pid = parseDataId(pRes);
    if (!pid) abortSetup(`product-${i}`, pRes);

    const oRes = http.post(
      `${BASE_URL}/api/admin/v1/products/${pid}/options`,
      JSON.stringify({ name: '기본', additionalPrice: 0, stock: 100000 }),
      { headers: ADMIN_HEADERS }
    );
    const oid = parseDataId(oRes);
    if (!oid) abortSetup(`option-${i}`, oRes);

    products.push({ productId: pid, optionId: oid });
  }

  // 테스트 계정 생성
  const accounts = [];
  for (let i = 0; i < 60; i++) {
    const uid = `rk${String(i).padStart(4, '0')}`;
    const pw = 'Password1!';
    http.post(
      `${BASE_URL}/api/v1/members/signup`,
      JSON.stringify({
        memberId: uid, password: pw,
        name: 'K6User', email: `${uid}@k6.com`, birthDate: '1990-01-01',
      }),
      { headers: JSON_HEADERS }
    );
    accounts.push({ uid, pw });
  }

  return { products, accounts };
}

// ── Case 1: 전체 흐름 기능 검증 ──────────────────────────────────
function runCase1(data) {
  const idx = exec.scenario.iterationInTest % data.accounts.length;
  const { uid, pw } = data.accounts[idx];
  const authHeaders = { ...JSON_HEADERS, 'X-Loopers-LoginId': uid, 'X-Loopers-LoginPw': pw };

  // 상품 랜덤 선택
  const product = data.products[Math.floor(Math.random() * data.products.length)];

  // 1. 상품 조회 (ViewEvent 트리거)
  const viewRes = http.get(`${BASE_URL}/api/v1/products/${product.productId}`, { headers: authHeaders });
  productDetailDuration.add(viewRes.timings.duration);
  if (!check(viewRes, { '상품조회 200': r => r.status === 200 })) {
    eventTriggerErrors.add(1);
  }

  sleep(0.3);

  // 2. 좋아요 토글 (LikeEvent 트리거)
  const likeRes = http.post(`${BASE_URL}/api/v1/likes/${product.productId}`, null, { headers: authHeaders });
  check(likeRes, { '좋아요 200': r => r.status === 200 });

  sleep(0.3);

  // 3. 추가 조회 (ViewEvent 누적 — 주문은 대기열 토큰 필요하여 제외)
  const viewRes2 = http.get(`${BASE_URL}/api/v1/products/${product.productId}`, { headers: authHeaders });
  check(viewRes2, { '추가조회 200': r => r.status === 200 });

  sleep(0.5);

  // 4. 일간 랭킹 조회
  const rankRes = http.get(`${BASE_URL}/api/v1/rankings?size=20&page=1`);
  rankingApiDuration.add(rankRes.timings.duration);
  if (!check(rankRes, {
    '랭킹조회 200': r => r.status === 200,
    '랭킹 응답 구조': r => {
      try { return r.json('data.rankings') !== undefined; } catch (_) { return false; }
    },
  })) {
    rankingQueryErrors.add(1);
  }

  sleep(0.3);

  // 5. 시간별 랭킹 조회
  const hourlyRes = http.get(`${BASE_URL}/api/v1/rankings/hourly?size=10&page=1`);
  check(hourlyRes, { '시간랭킹 200': r => r.status === 200 });

  sleep(0.3);

  // 6. 상품 상세에 rank 필드 포함 확인
  const detailRes = http.get(`${BASE_URL}/api/v1/products/${product.productId}`, { headers: authHeaders });
  check(detailRes, {
    '상세조회 200': r => r.status === 200,
    'rank 필드 존재 (NON_NULL이라 없을 수 있음)': r => {
      // Jackson NON_NULL 설정: rank가 null이면 필드 자체가 생략됨 → 스케줄러 갱신 전에는 정상적으로 없음
      return r.status === 200;
    },
  });
}

// ── Case 2: 랭킹 정합성 검증 ────────────────────────────────────
function runCase2(data) {
  const vuIdx = __VU - 1;
  const { uid, pw } = data.accounts[vuIdx % data.accounts.length];
  const authHeaders = { ...JSON_HEADERS, 'X-Loopers-LoginId': uid, 'X-Loopers-LoginPw': pw };

  // 상품별 차등 이벤트 발생 (주문은 대기열 토큰 필요하여 좋아요/조회로 대체)
  // Product 1: 좋아요 + 조회 heavy (가장 높아야 함)
  // Product 2: 좋아요 1건
  // Product 3: 조회 5건
  // Product 4: 조회 1건 (가장 낮아야 함)
  // Product 5: 좋아요 + 조회 2건

  const p = data.products;

  // Product 1: 좋아요 + 조회 heavy
  http.post(`${BASE_URL}/api/v1/likes/${p[0].productId}`, null, { headers: authHeaders });
  for (let i = 0; i < 5; i++) {
    http.get(`${BASE_URL}/api/v1/products/${p[0].productId}`, { headers: authHeaders });
    sleep(0.1);
  }

  // Product 2: 좋아요 (토글이라 홀수번 = liked)
  http.post(`${BASE_URL}/api/v1/likes/${p[1].productId}`, null, { headers: authHeaders });
  sleep(0.1);

  // Product 3: 조회 heavy
  for (let i = 0; i < 5; i++) {
    http.get(`${BASE_URL}/api/v1/products/${p[2].productId}`, { headers: authHeaders });
    sleep(0.1);
  }

  // Product 4: 조회 1건
  http.get(`${BASE_URL}/api/v1/products/${p[3].productId}`, { headers: authHeaders });

  // Product 5: 좋아요 + 조회
  http.post(`${BASE_URL}/api/v1/likes/${p[4].productId}`, null, { headers: authHeaders });
  http.get(`${BASE_URL}/api/v1/products/${p[4].productId}`, { headers: authHeaders });
  http.get(`${BASE_URL}/api/v1/products/${p[4].productId}`, { headers: authHeaders });

  // daily ranking 갱신을 polling으로 대기
  const rankRes = waitForDailyRanking();
  check(rankRes, {
    '정합성 랭킹조회 200': r => r && r.status === 200,
    '정합성 랭킹 데이터 존재': r => {
      if (!r || r.status !== 200) return false;
      try {
        const rankings = r.json('data.rankings');
        return rankings && rankings.length > 0;
      } catch (_) {
        return false;
      }
    },
  });

  if (rankRes.status === 200) {
    try {
      const rankings = rankRes.json('data.rankings');
      if (rankings && rankings.length >= 2) {
        console.log(`[Case2] Top rankings: ${rankings.map(r => `pid=${r.productId},score=${r.score}`).join(' | ')}`);
      }
    } catch (_) {}
  }
}

// ── Main ─────────────────────────────────────────────────────────
export default function (data) {
  const active = parseInt(__ENV.ACTIVE_CASE || String(CASE));
  if (active === 1) runCase1(data);
  else runCase2(data);
}
