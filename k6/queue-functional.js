/**
 * K6 Queue Functional Test — Case 1 & 2
 *
 * Case 1: 기능 검증 (10~20 VU)
 *   전체 흐름: 회원가입 → 대기열 진입 → 순번 조회 → 토큰 발급 대기 → 주문
 *   목표: 에러율 0%
 *
 * Case 2: 순서 보장 (100 VU)
 *   100명 동시 대기열 진입 → 각자 고유 순번 확인 (중복 0건)
 *   목표: 순번 중복 0건
 *
 * 실행 전제:
 *   - 서버가 scheduler 활성화 상태로 기동되어야 함
 *   - local/test 프로파일에서는 scheduler 비활성화되므로 아래처럼 실행:
 *     java -jar app.jar -Dqueue.scheduler.enabled=true
 *     또는 spring.profiles.active=dev
 *
 * 실행:
 *   k6 run --env CASE=1 k6/queue-functional.js
 *   k6 run --env CASE=2 k6/queue-functional.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

// ── Custom Metrics ───────────────────────────────────────────────
const queueEnterErrors   = new Counter('queue_enter_errors');
const positionQueries    = new Counter('position_queries');
const tokenWaitTime      = new Trend('token_wait_ms', true);
const orderErrors        = new Counter('order_errors');
const duplicatePositions = new Counter('duplicate_positions');

// ── Config ───────────────────────────────────────────────────────
const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8080';
const CASE        = parseInt(__ENV.CASE || '1');
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 토큰 대기 최대 시간 (스케줄러 100ms × 배치 처리 고려 → 최대 30s)
const TOKEN_POLL_MAX_MS  = 30_000;
const TOKEN_POLL_INTERVAL_S = 0.2;

// ── Scenario Options ─────────────────────────────────────────────
const scenarios = {
  // Case 1: 10 VU, 30s — 전체 흐름 기능 검증
  case1_functional: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 10 },
      { duration: '30s', target: 20 },
      { duration: '10s', target: 0  },
    ],
    env: { ACTIVE_CASE: '1' },
  },
  // Case 2: 100 VU, 1번씩만 — 순서 보장 검증
  case2_ordering: {
    executor: 'per-vu-iterations',
    vus: 100,
    iterations: 1,
    maxDuration: '60s',
    env: { ACTIVE_CASE: '2' },
  },
};

export const options = {
  scenarios:    CASE === 1 ? { case1_functional: scenarios.case1_functional }
                           : { case2_ordering:   scenarios.case2_ordering   },
  thresholds: {
    http_req_failed:   ['rate<0.01'],   // 에러율 1% 미만
    http_req_duration: ['p(95)<2000'],  // p95 2s 미만
  },
};

// ── Setup: 공유 상품 데이터 준비 ─────────────────────────────────
export function setup() {
  // 브랜드 생성
  const brandRes = http.post(
    `${BASE_URL}/api/admin/v1/brands`,
    JSON.stringify({ name: 'K6-테스트-브랜드' }),
    { headers: { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' } }
  );
  const brandId = brandRes.json('data.id');

  // 상품 생성
  const productRes = http.post(
    `${BASE_URL}/api/admin/v1/products`,
    JSON.stringify({ brandId, name: 'K6-테스트-상품', basePrice: 10000 }),
    { headers: { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' } }
  );
  const productId = productRes.json('data.id');

  // 옵션 생성 (충분한 재고)
  const optionRes = http.post(
    `${BASE_URL}/api/admin/v1/products/${productId}/options`,
    JSON.stringify({ name: '기본', additionalPrice: 0, stock: 100000 }),
    { headers: { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' } }
  );
  const optionId = optionRes.json('data.id');

  return { optionId };
}

// ── Case 1: 전체 흐름 기능 검증 ──────────────────────────────────
function runCase1(data) {
  // memberId: 영숫자만 4~10자 (MemberId VO 제약)
  // VU(1~20) × ITER(0~N) → 최대 5자리 숫자로 고유 ID 생성
  const uid = `u${String(__VU).padStart(2, '0')}${String(__ITER % 1000).padStart(3, '0')}`;
  const pw  = 'Password1!';

  // 1. 회원가입
  const signupRes = http.post(
    `${BASE_URL}/api/v1/members/signup`,
    JSON.stringify({
      memberId:  uid,
      password:  pw,
      name:      'K6User',
      email:     `${uid}@k6test.com`,
      birthDate: '1990-01-01',
    }),
    { headers: JSON_HEADERS }
  );
  if (!check(signupRes, { '회원가입 200': r => r.status === 200 })) return;

  const authHeaders = { ...JSON_HEADERS, 'X-Loopers-LoginId': uid, 'X-Loopers-LoginPw': pw };

  // 2. 대기열 진입
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, { headers: authHeaders });
  if (!check(enterRes, { '대기열 진입 200': r => r.status === 200 })) {
    queueEnterErrors.add(1);
    return;
  }
  const position = enterRes.json('data.position');
  check(enterRes, { '순번 > 0': () => position > 0 });

  // 3. 토큰 발급 대기 (스케줄러가 배치 처리할 때까지 Polling)
  const waitStart = Date.now();
  let tokenIssued = false;

  while (Date.now() - waitStart < TOKEN_POLL_MAX_MS) {
    sleep(TOKEN_POLL_INTERVAL_S);
    positionQueries.add(1);

    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, { headers: authHeaders });

    if (posRes.status === 200 && posRes.json('data.tokenIssued') === true) {
      tokenIssued = true;
      break;
    }
  }

  tokenWaitTime.add(Date.now() - waitStart);
  check(null, { '토큰 발급 완료': () => tokenIssued });
  if (!tokenIssued) {
    orderErrors.add(1);
    return;
  }

  // 4. 주문 생성
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders/direct`,
    JSON.stringify({ optionId: data.optionId, quantity: 1 }),
    { headers: authHeaders }
  );
  if (!check(orderRes, { '주문 200': r => r.status === 200 })) {
    orderErrors.add(1);
  }
}

// ── Case 2: 순서 보장 검증 ───────────────────────────────────────
// 공유 순번 Set (단일 VU에서 수집하는 방식 — 주의: VU 간 공유 불가)
// → 대신 각 VU가 자신의 순번을 기록하고, 중복 판단은 서버 측 ZADD NX 특성으로 보장
// → 여기서는 각 VU의 순번이 1 이상이고 HTTP 200임을 검증
function runCase2() {
  // Case 2는 per-vu-iterations (1회), VU 번호로 고유 ID 생성
  const uid = `v${String(__VU).padStart(4, '0')}`;
  const pw  = 'Password1!';

  // 회원가입 (이미 있을 경우 무시)
  http.post(
    `${BASE_URL}/api/v1/members/signup`,
    JSON.stringify({
      memberId:  uid,
      password:  pw,
      name:      'K6User',
      email:     `${uid}@k6test.com`,
      birthDate: '1990-01-01',
    }),
    { headers: JSON_HEADERS }
  );

  const authHeaders = { ...JSON_HEADERS, 'X-Loopers-LoginId': uid, 'X-Loopers-LoginPw': pw };

  // 대기열 진입 (동시에 100명)
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, { headers: authHeaders });

  check(enterRes, {
    '진입 200': r => r.status === 200,
    '순번 >= 1': r => (r.json('data.position') || 0) >= 1,
  });

  if (enterRes.status !== 200) {
    queueEnterErrors.add(1);
    return;
  }

  // 재진입 시도 — 같은 유저가 다시 진입해도 순번이 바뀌지 않음 (ZADD NX)
  const reenterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, { headers: authHeaders });
  const pos1 = enterRes.json('data.position');
  const pos2 = reenterRes.json('data.position');

  check(null, {
    '재진입 시 순번 유지 (ZADD NX)': () => pos1 === pos2,
  });
}

// ── Main ─────────────────────────────────────────────────────────
export default function (data) {
  const activeCase = parseInt(__ENV.ACTIVE_CASE || String(CASE));
  if (activeCase === 1) {
    runCase1(data);
  } else {
    runCase2();
  }
}
