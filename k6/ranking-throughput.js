/**
 * K6 Ranking Throughput Test — Case 3 & 4
 *
 * Case 3: 랭킹 API 읽기 부하 (200~500 VU)
 *   랭킹 조회 API에 집중 부하 → p95 응답시간, TPS 측정
 *   목표: p95 < 500ms, 에러율 < 1%
 *
 * Case 4: 혼합 부하 (500~1000 VU)
 *   읽기(랭킹 조회 70%) + 쓰기(이벤트 발생 30%) 혼합
 *   단계별 VU 증가 → 포화 지점 탐색
 *   목표: 한계점 식별
 *
 * 실행:
 *   k6 run --env CASE=3 k6/ranking-throughput.js
 *   k6 run --env CASE=4 k6/ranking-throughput.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import exec from 'k6/execution';

// ── Custom Metrics ───────────────────────────────────────────────
const rankingReadDuration  = new Trend('ranking_read_duration_ms', true);
const hourlyReadDuration   = new Trend('hourly_read_duration_ms', true);
const productDetailMs      = new Trend('product_detail_duration_ms', true);
const readErrors           = new Counter('read_errors');
const writeErrors          = new Counter('write_errors');
const totalReads           = new Counter('total_reads');
const totalWrites          = new Counter('total_writes');

// ── Config ───────────────────────────────────────────────────────
const BASE_URL     = __ENV.BASE_URL || 'http://localhost:8080';
const CASE         = parseInt(__ENV.CASE || '3');
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const ADMIN_HEADERS = { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' };

function parseDataId(res) {
  try { return res.json('data.id'); } catch (_) { return null; }
}

// ── Scenarios ────────────────────────────────────────────────────
const scenarios = {
  case3_read_heavy: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 100 },
      { duration: '20s', target: 200 },
      { duration: '30s', target: 500 },
      { duration: '20s', target: 500 },
      { duration: '10s', target: 0 },
    ],
    env: { ACTIVE_CASE: '3' },
  },
  case4_mixed: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 100 },
      { duration: '15s', target: 300 },
      { duration: '15s', target: 500 },
      { duration: '15s', target: 800 },
      { duration: '15s', target: 1000 },
      { duration: '10s', target: 0 },
    ],
    env: { ACTIVE_CASE: '4' },
  },
};

export const options = {
  scenarios: CASE === 3
    ? { case3_read_heavy: scenarios.case3_read_heavy }
    : { case4_mixed: scenarios.case4_mixed },
  thresholds: CASE === 3
    ? {
        ranking_read_duration_ms: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
      }
    : {
        http_req_failed: ['rate<0.05'],
      },
};

// ── Setup: 테스트 데이터 준비 ────────────────────────────────────
export function setup() {
  // 브랜드
  const brandRes = http.post(
    `${BASE_URL}/api/admin/v1/brands`,
    JSON.stringify({ name: 'K6-Throughput-Brand' }),
    { headers: ADMIN_HEADERS }
  );
  const brandId = parseDataId(brandRes);

  // 상품 10개
  const products = [];
  for (let i = 1; i <= 10; i++) {
    const pRes = http.post(
      `${BASE_URL}/api/admin/v1/products`,
      JSON.stringify({ brandId, name: `K6-TP-Product-${i}`, basePrice: 5000 * i }),
      { headers: ADMIN_HEADERS }
    );
    const pid = parseDataId(pRes);

    const oRes = http.post(
      `${BASE_URL}/api/admin/v1/products/${pid}/options`,
      JSON.stringify({ name: '기본', additionalPrice: 0, stock: 999999 }),
      { headers: ADMIN_HEADERS }
    );
    const oid = parseDataId(oRes);
    products.push({ productId: pid, optionId: oid });
  }

  // 초기 이벤트 — 랭킹 데이터 시딩 (각 상품에 조회 이벤트)
  for (const p of products) {
    for (let j = 0; j < 5; j++) {
      http.get(`${BASE_URL}/api/v1/products/${p.productId}`);
    }
  }

  // 스케줄러가 ranking:all 갱신하도록 대기
  console.log('Waiting 15s for scheduler to populate ranking:all...');
  sleep(15);

  // 계정 생성 (Case 4 쓰기용)
  const accounts = [];
  const accountCount = CASE === 4 ? 200 : 50;
  for (let i = 0; i < accountCount; i++) {
    const uid = `tp${String(i).padStart(4, '0')}`;
    const pw = 'Password1!';
    http.post(
      `${BASE_URL}/api/v1/members/signup`,
      JSON.stringify({
        memberId: uid, password: pw,
        name: 'K6User', email: `${uid}@k6tp.com`, birthDate: '1990-01-01',
      }),
      { headers: JSON_HEADERS }
    );
    accounts.push({ uid, pw });
  }

  return { products, accounts };
}

// ── Case 3: 읽기 집중 부하 ───────────────────────────────────────
function runCase3(data) {
  const action = Math.random();

  if (action < 0.5) {
    // 50%: 일간 랭킹 조회
    const page = Math.random() < 0.8 ? 1 : Math.floor(Math.random() * 3) + 1;
    const res = http.get(`${BASE_URL}/api/v1/rankings?size=20&page=${page}`);
    rankingReadDuration.add(res.timings.duration);
    totalReads.add(1);
    if (!check(res, { '랭킹 200': r => r.status === 200 })) readErrors.add(1);
  } else if (action < 0.8) {
    // 30%: 시간별 랭킹
    const res = http.get(`${BASE_URL}/api/v1/rankings/hourly?size=10&page=1`);
    hourlyReadDuration.add(res.timings.duration);
    totalReads.add(1);
    if (!check(res, { '시간랭킹 200': r => r.status === 200 })) readErrors.add(1);
  } else {
    // 20%: 상품 상세 (rank 포함)
    const p = data.products[Math.floor(Math.random() * data.products.length)];
    const res = http.get(`${BASE_URL}/api/v1/products/${p.productId}`);
    productDetailMs.add(res.timings.duration);
    totalReads.add(1);
    if (!check(res, { '상세 200': r => r.status === 200 })) readErrors.add(1);
  }

  sleep(0.1);
}

// ── Case 4: 읽기 + 쓰기 혼합 부하 ───────────────────────────────
function runCase4(data) {
  const action = Math.random();
  const accIdx = exec.scenario.iterationInTest % data.accounts.length;
  const { uid, pw } = data.accounts[accIdx];
  const authHeaders = { ...JSON_HEADERS, 'X-Loopers-LoginId': uid, 'X-Loopers-LoginPw': pw };

  if (action < 0.5) {
    // 50%: 랭킹 조회
    const page = Math.random() < 0.8 ? 1 : Math.floor(Math.random() * 3) + 1;
    const res = http.get(`${BASE_URL}/api/v1/rankings?size=20&page=${page}`);
    rankingReadDuration.add(res.timings.duration);
    totalReads.add(1);
    if (!check(res, { '랭킹 200': r => r.status === 200 })) readErrors.add(1);
  } else if (action < 0.7) {
    // 20%: 상품 조회 (ViewEvent 트리거)
    const p = data.products[Math.floor(Math.random() * data.products.length)];
    const res = http.get(`${BASE_URL}/api/v1/products/${p.productId}`, { headers: authHeaders });
    productDetailMs.add(res.timings.duration);
    totalWrites.add(1);
    if (!check(res, { '조회 200': r => r.status === 200 })) writeErrors.add(1);
  } else if (action < 0.85) {
    // 15%: 좋아요
    const p = data.products[Math.floor(Math.random() * data.products.length)];
    const res = http.post(`${BASE_URL}/api/v1/likes/${p.productId}`, null, { headers: authHeaders });
    totalWrites.add(1);
    if (!check(res, { '좋아요 200': r => r.status === 200 })) writeErrors.add(1);
  } else {
    // 15%: 추가 조회 (주문은 대기열 토큰 필요하여 제외)
    const p = data.products[Math.floor(Math.random() * data.products.length)];
    const res = http.get(`${BASE_URL}/api/v1/products/${p.productId}`, { headers: authHeaders });
    productDetailMs.add(res.timings.duration);
    totalWrites.add(1);
    if (!check(res, { '추가조회 200': r => r.status === 200 })) writeErrors.add(1);
  }

  sleep(0.05);
}

// ── Main ─────────────────────────────────────────────────────────
export default function (data) {
  const active = parseInt(__ENV.ACTIVE_CASE || String(CASE));
  if (active === 3) runCase3(data);
  else runCase4(data);
}
