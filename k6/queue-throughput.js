/**
 * K6 Queue Throughput Test — Case 3 & 4
 *
 * Case 3: 스케줄러 처리량 검증 (500 VU)
 *   500명 동시 진입 후 모두 토큰 받는 데 걸리는 시간 측정
 *   설계 TPS=140 기준: 500명 → 약 3.5s 이내 처리 예상
 *   목표: 실제 TPS ≥ 설계값의 80% (112 TPS)
 *
 * Case 4: 부하 한계 탐색 (1000~2000 VU)
 *   단계별 VU 증가 → Tomcat 스레드(200), DB 커넥션(40), Redis 응답시간 포화 지점 탐색
 *   목표: 한계점 식별 (에러율 급증 구간)
 *
 * 실행:
 *   k6 run --env CASE=3 k6/queue-throughput.js
 *   k6 run --env CASE=4 k6/queue-throughput.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate, Gauge } from 'k6/metrics';

// ── Custom Metrics ───────────────────────────────────────────────
const tokenWaitMs      = new Trend('token_wait_ms', true);
const queueDepth       = new Trend('queue_depth');
const tokenIssued      = new Counter('token_issued');
const orderSuccess     = new Counter('order_success');
const enterErrors      = new Counter('enter_errors');
const positionPollRate = new Counter('position_polls');

// ── Config ───────────────────────────────────────────────────────
const BASE_URL     = __ENV.BASE_URL  || 'http://localhost:8080';
const CASE         = parseInt(__ENV.CASE || '3');
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const TOKEN_POLL_MAX_MS   = 60_000;  // Case 3~4는 대기열 깊어지므로 여유 있게
const TOKEN_POLL_INTERVAL = 0.3;     // 300ms polling

// ── Scenario Options ─────────────────────────────────────────────
export const options = CASE === 3
  ? {
      // Case 3: 500 VU, 각 1회 — 동시 진입 후 처리량 측정
      scenarios: {
        case3_throughput: {
          executor: 'per-vu-iterations',
          vus: 500,
          iterations: 1,
          maxDuration: '3m',
        },
      },
      thresholds: {
        http_req_duration:    ['p(95)<3000'],
        'token_wait_ms':      ['p(95)<15000'],  // 500명 / 140 TPS ≈ 3.5s, 여유 포함
      },
    }
  : {
      // Case 4: ramping — 점진적 부하 증가로 한계점 탐색
      scenarios: {
        case4_stress: {
          executor: 'ramping-arrival-rate',
          startRate: 50,
          timeUnit: '1s',
          preAllocatedVUs: 200,
          maxVUs: 2000,
          stages: [
            { duration: '30s', target: 100  },  // 워밍업
            { duration: '30s', target: 300  },  // 중간 부하
            { duration: '30s', target: 700  },  // 높은 부하
            { duration: '30s', target: 1400 },  // 설계 TPS 한계
            { duration: '30s', target: 2000 },  // 한계 초과
            { duration: '30s', target: 100  },  // 회복
          ],
        },
      },
      thresholds: {
        http_req_duration: ['p(95)<5000'],
        http_req_failed:   ['rate<0.30'],  // 한계 탐색이므로 관대하게
      },
    };

// ── Setup: 유저 사전 등록 ────────────────────────────────────────
export function setup() {
  const prefix = CASE === 3 ? 'c3' : 'c4';
  const count  = CASE === 3 ? 500 : 300;  // Case 4는 arrival-rate이라 일부만 pre-register
  const registered = [];

  // 배치 등록 (직렬)
  for (let i = 0; i < count; i++) {
    const uid = `${prefix}${String(i).padStart(5, '0')}`;
    const res = http.post(
      `${BASE_URL}/api/v1/members/signup`,
      JSON.stringify({
        memberId:  uid,
        password:  'Password1!',
        name:      'ThroughputUser',
        email:     `${uid}@k6thr.com`,
        birthDate: '1990-01-01',
      }),
      { headers: JSON_HEADERS }
    );
    // 이미 존재하면 무시 (200 or 400 모두 허용)
    registered.push(uid);
  }

  // 상품 준비
  const brandRes = http.post(
    `${BASE_URL}/api/admin/v1/brands`,
    JSON.stringify({ name: `Thr-Brand-${CASE}` }),
    { headers: { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' } }
  );
  const brandId = brandRes.json('data.id');

  const productRes = http.post(
    `${BASE_URL}/api/admin/v1/products`,
    JSON.stringify({ brandId, name: `Thr-Product-${CASE}`, basePrice: 10000 }),
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

// ── Case 3: 처리량 측정 ──────────────────────────────────────────
function runCase3(data) {
  const idx = (__VU - 1) % data.registered.length;
  const uid = data.registered[idx];
  const pw  = 'Password1!';
  const authHeaders = { ...JSON_HEADERS, 'X-Loopers-LoginId': uid, 'X-Loopers-LoginPw': pw };

  // 1. 대기열 진입
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, { headers: authHeaders });
  if (!check(enterRes, { '진입 200': r => r.status === 200 })) {
    enterErrors.add(1);
    return;
  }

  const myPosition = enterRes.json('data.position');

  // 2. 토큰 발급 대기
  const waitStart = Date.now();
  let gotToken = false;

  while (Date.now() - waitStart < TOKEN_POLL_MAX_MS) {
    sleep(TOKEN_POLL_INTERVAL);
    positionPollRate.add(1);

    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, { headers: authHeaders });

    if (posRes.status === 200 && posRes.json('data.tokenIssued') === true) {
      gotToken = true;
      break;
    }

    if (posRes.status === 200) {
      const remaining = posRes.json('data.position');
      queueDepth.add(remaining);
    }
  }

  const waitMs = Date.now() - waitStart;
  tokenWaitMs.add(waitMs);

  check(null, { '토큰 발급 완료': () => gotToken });
  if (!gotToken) return;

  tokenIssued.add(1);

  // 3. 주문
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders/direct`,
    JSON.stringify({ optionId: data.optionId, quantity: 1 }),
    { headers: authHeaders }
  );
  if (check(orderRes, { '주문 200': r => r.status === 200 })) {
    orderSuccess.add(1);
  }
}

// ── Case 4: 부하 한계 탐색 ───────────────────────────────────────
// arrival-rate 기반 — 단순 진입 + 순번 조회만 반복 (토큰 대기 없음)
// 목적: API 서버 응답시간 포화 지점 탐색
function runCase4(data) {
  const idx = __VU % data.registered.length;
  const uid = data.registered[idx] || `c4${String(__VU % 300).padStart(5, '0')}`;
  const pw  = 'Password1!';
  const authHeaders = { ...JSON_HEADERS, 'X-Loopers-LoginId': uid, 'X-Loopers-LoginPw': pw };

  // 대기열 진입
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, { headers: authHeaders });
  check(enterRes, { '진입 성공': r => r.status === 200 });

  // 순번 조회 (1회 — 부하 측정 목적)
  const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, { headers: authHeaders });
  check(posRes, { '조회 성공': r => r.status === 200 || r.status === 404 });
}

// ── Main ─────────────────────────────────────────────────────────
export default function (data) {
  if (CASE === 3) {
    runCase3(data);
  } else {
    runCase4(data);
  }
}
