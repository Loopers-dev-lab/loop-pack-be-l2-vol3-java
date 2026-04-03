/**
 * K6 Queue Thundering Herd Test — Case 5
 *
 * 시나리오:
 *   스케줄러가 14명씩 배치로 토큰 발급 → 14명이 동시에 주문 API 호출
 *   → DB/Order 서버에 순간 스파이크 발생 여부 측정
 *
 * 측정 목표:
 *   - 현재 구현(완화 없음): 주문 타이밍이 14개씩 클러스터링되는지 확인
 *   - 응답시간 분포: 스파이크 구간 vs 정상 구간 비교
 *   - Thundering Herd 완화 필요성 판단 근거 데이터 수집
 *
 * 실행:
 *   k6 run k6/queue-thundering-herd.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

// ── Custom Metrics ───────────────────────────────────────────────
const orderLatency    = new Trend('order_latency_ms', true);
const tokenWaitMs     = new Trend('token_wait_ms', true);
const orderSuccess    = new Counter('order_success');
const orderFail       = new Counter('order_fail');
const positionPolls   = new Counter('position_polls');

// ── Config ───────────────────────────────────────────────────────
const BASE_URL     = __ENV.BASE_URL   || 'http://localhost:8080';
const VU_COUNT     = parseInt(__ENV.VUS || '140');  // 배치(14) × 10배 = 140명
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const TOKEN_POLL_MAX_MS   = 120_000;
const TOKEN_POLL_INTERVAL = 0.2;

export const options = {
  scenarios: {
    thundering_herd: {
      executor: 'per-vu-iterations',
      vus: VU_COUNT,
      iterations: 1,
      maxDuration: '3m',
    },
  },
  thresholds: {
    'order_latency_ms': ['p(95)<3000'],
    'http_req_duration': ['p(95)<5000'],
  },
};

// ── Setup ────────────────────────────────────────────────────────
export function setup() {
  const registered = [];

  for (let i = 0; i < VU_COUNT; i++) {
    const uid = `th${String(i).padStart(5, '0')}`;
    http.post(
      `${BASE_URL}/api/v1/members/signup`,
      JSON.stringify({
        memberId:  uid,
        password:  'Password1!',
        name:      'ThUser',
        email:     `${uid}@k6th.com`,
        birthDate: '1990-01-01',
      }),
      { headers: JSON_HEADERS }
    );
    registered.push(uid);
  }

  const brandRes = http.post(
    `${BASE_URL}/api/admin/v1/brands`,
    JSON.stringify({ name: 'TH-Brand' }),
    { headers: { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' } }
  );
  const brandId = brandRes.json('data.id');

  const productRes = http.post(
    `${BASE_URL}/api/admin/v1/products`,
    JSON.stringify({ brandId, name: 'TH-Product', basePrice: 10000 }),
    { headers: { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' } }
  );
  const productId = productRes.json('data.id');

  const optionRes = http.post(
    `${BASE_URL}/api/admin/v1/products/${productId}/options`,
    JSON.stringify({ name: '기본', additionalPrice: 0, stock: 999999 }),
    { headers: { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' } }
  );
  const optionId = optionRes.json('data.id');

  return { registered, optionId };
}

// ── Main ─────────────────────────────────────────────────────────
export default function (data) {
  const uid = data.registered[__VU - 1];
  const pw  = 'Password1!';
  const authHeaders = { ...JSON_HEADERS, 'X-Loopers-LoginId': uid, 'X-Loopers-LoginPw': pw };

  // 1. 대기열 진입 (모든 VU가 동시에 진입 → 배치 처리 시 Thundering Herd 발생)
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, { headers: authHeaders });
  if (!check(enterRes, { '진입 200': r => r.status === 200 })) return;

  // 2. 토큰 발급 대기
  const waitStart = Date.now();
  let gotToken = false;

  while (Date.now() - waitStart < TOKEN_POLL_MAX_MS) {
    positionPolls.add(1);

    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, { headers: authHeaders });
    if (posRes.status === 200 && posRes.json('data.tokenIssued') === true) {
      gotToken = true;
      break;
    }

    // 서버가 제공한 nextPollIntervalMs 우선 사용 (적응형 폴링), 없으면 고정값 폴백
    const nextInterval = (posRes.status === 200 && posRes.json('data.nextPollIntervalMs'))
      ? posRes.json('data.nextPollIntervalMs') / 1000
      : TOKEN_POLL_INTERVAL;
    sleep(nextInterval);
  }

  tokenWaitMs.add(Date.now() - waitStart);
  if (!gotToken) return;

  // 3. 주문 — 타이밍 측정 (배치 단위로 동시 주문 스파이크 발생 여부)
  const orderStart = Date.now();
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders/direct`,
    JSON.stringify({ optionId: data.optionId, quantity: 1 }),
    { headers: authHeaders }
  );
  orderLatency.add(Date.now() - orderStart);

  if (check(orderRes, { '주문 200': r => r.status === 200 })) {
    orderSuccess.add(1);
  } else {
    orderFail.add(1);
  }
}
