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
import exec from 'k6/execution';

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
const CASE4_FLOW   = __ENV.CASE4_FLOW || 'preseed';
const SIGNUP_BATCH_SIZE = parseInt(__ENV.SIGNUP_BATCH_SIZE || '50');
const CASE4_ACCOUNT_COUNT = parseInt(__ENV.CASE4_ACCOUNT_COUNT || '5000');
const CASE4_STAGE_DURATION = __ENV.CASE4_STAGE_DURATION || '5s';
const CASE4_TARGETS = (__ENV.CASE4_TARGETS || '20,40,80,120,160,20')
  .split(',')
  .map(v => parseInt(v.trim(), 10))
  .filter(v => !Number.isNaN(v));
const SETUP_TIMEOUT = __ENV.SETUP_TIMEOUT || '10m';

const TOKEN_POLL_MAX_MS   = 60_000;  // Case 3~4는 대기열 깊어지므로 여유 있게
const TOKEN_POLL_INTERVAL = 0.3;     // fallback polling interval
const FAILURE_LOG_LIMIT = parseInt(__ENV.FAILURE_LOG_LIMIT || '5');
let enterFailureLogs = 0;

function compactRunPrefix(caseNo) {
  const suffix = Math.random().toString(36).slice(2, 5);
  return `${caseNo === 3 ? 't3' : 't4'}${suffix}`;
}

function parseJson(res) {
  try {
    return res.json();
  } catch (_) {
    return null;
  }
}

function parseDataId(res) {
  try {
    return res.json('data.id');
  } catch (_) {
    return null;
  }
}

function logSetupFailure(stepName, res) {
  console.error(`[setup:${stepName}] FAILED (${res.status}): ${res.body}`);
}

function requireSetupId(stepName, res) {
  const id = parseDataId(res);
  if (res.status !== 200 || !id) {
    logSetupFailure(stepName, res);
    throw new Error(`setup failed at ${stepName}`);
  }
  return id;
}

function isDuplicateSignup(res) {
  if (res.status !== 409) {
    return false;
  }
  const body = parseJson(res);
  const message = body?.meta?.message || '';
  return message.includes('이미 존재하는 회원 ID');
}

function validateSignup(stepName, res) {
  if (res.status === 200 || isDuplicateSignup(res)) {
    return;
  }
  logSetupFailure(stepName, res);
  throw new Error(`setup failed at ${stepName}`);
}

function buildSignupRequest(uid, namePrefix, emailDomain) {
  return {
    method: 'POST',
    url: `${BASE_URL}/api/v1/members/signup`,
    body: JSON.stringify({
      memberId: uid,
      password: 'Password1!',
      name: namePrefix,
      email: `${uid}@${emailDomain}`,
      birthDate: '1990-01-01',
    }),
    params: { headers: JSON_HEADERS },
  };
}

function seedMembers(prefix, count) {
  const registered = [];

  for (let start = 0; start < count; start += SIGNUP_BATCH_SIZE) {
    const batchSize = Math.min(SIGNUP_BATCH_SIZE, count - start);
    const requests = [];
    const uids = [];

    for (let offset = 0; offset < batchSize; offset++) {
      const uid = `${prefix}${String(start + offset).padStart(5, '0')}`;
      uids.push(uid);
      requests.push(buildSignupRequest(uid, 'ThroughputUser', 'k6thr.com'));
    }

    const responses = http.batch(requests);
    for (let i = 0; i < responses.length; i++) {
      validateSignup(`signup:${uids[i]}`, responses[i]);
      registered.push(uids[i]);
    }
  }

  return registered;
}

function logEnterFailure(caseName, uid, res) {
  if (enterFailureLogs >= FAILURE_LOG_LIMIT) {
    return;
  }
  enterFailureLogs += 1;
  console.error(`[${caseName}] enter failed uid=${uid} status=${res.status} body=${res.body}`);
}

function projectedCase4Iterations() {
  const stages = [50, ...CASE4_TARGETS];
  let projected = 0;

  for (let i = 1; i < stages.length; i++) {
    projected += Math.ceil(((stages[i - 1] + stages[i]) / 2) * parseDurationSeconds(CASE4_STAGE_DURATION));
  }

  return projected;
}

function parseDurationSeconds(duration) {
  const match = duration.match(/^(\d+)(ms|s|m)$/);
  if (!match) {
    throw new Error(`unsupported duration format: ${duration}`);
  }
  const value = parseInt(match[1], 10);
  const unit = match[2];

  if (unit === 'ms') return value / 1000;
  if (unit === 's') return value;
  return value * 60;
}

function case4Stages() {
  return CASE4_TARGETS.map(target => ({ duration: CASE4_STAGE_DURATION, target }));
}

// ── Scenario Options ─────────────────────────────────────────────
export const options = CASE === 3
  ? {
      setupTimeout: SETUP_TIMEOUT,
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
      setupTimeout: SETUP_TIMEOUT,
      // Case 4: ramping — 점진적 부하 증가로 한계점 탐색
      scenarios: {
        case4_stress: {
          executor: 'ramping-arrival-rate',
          startRate: 50,
          timeUnit: '1s',
          preAllocatedVUs: 200,
          maxVUs: 2000,
          stages: case4Stages(),
        },
      },
      thresholds: {
        http_req_duration: ['p(95)<5000'],
        http_req_failed:   ['rate<0.30'],  // 한계 탐색이므로 관대하게
      },
    };

// ── Setup: 유저 사전 등록 ────────────────────────────────────────
export function setup() {
  const prefix = compactRunPrefix(CASE);
  const count = CASE === 3 ? 500 : CASE4_FLOW === 'preseed' ? CASE4_ACCOUNT_COUNT : 0;

  if (CASE === 4 && CASE4_FLOW === 'preseed' && count < projectedCase4Iterations()) {
    throw new Error(`CASE4_ACCOUNT_COUNT=${count} is smaller than projected iterations=${projectedCase4Iterations()}. Increase seeded accounts or lower CASE4_TARGETS/CASE4_STAGE_DURATION.`);
  }
  const registered = seedMembers(prefix, count);

  // 상품 준비
  const brandRes = http.post(
    `${BASE_URL}/api/admin/v1/brands`,
    JSON.stringify({ name: `Thr-Brand-${CASE}` }),
    { headers: { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' } }
  );
  const brandId = requireSetupId('brand', brandRes);

  const productRes = http.post(
    `${BASE_URL}/api/admin/v1/products`,
    JSON.stringify({ brandId, name: `Thr-Product-${CASE}`, basePrice: 10000 }),
    { headers: { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' } }
  );
  const productId = requireSetupId('product', productRes);

  const optionRes = http.post(
    `${BASE_URL}/api/admin/v1/products/${productId}/options`,
    JSON.stringify({ name: '기본', additionalPrice: 0, stock: 999999 }),
    { headers: { ...JSON_HEADERS, 'X-Loopers-Ldap': 'admin' } }
  );
  const optionId = requireSetupId('option', optionRes);

  return { registered, optionId, prefix };
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
    logEnterFailure('case3', uid, enterRes);
    return;
  }

  const myPosition = enterRes.json('data.position');

  // 2. 토큰 발급 대기
  const waitStart = Date.now();
  let gotToken = false;

  while (Date.now() - waitStart < TOKEN_POLL_MAX_MS) {
    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, { headers: authHeaders });
    positionPollRate.add(1);

    if (posRes.status === 200 && posRes.json('data.tokenIssued') === true) {
      gotToken = true;
      break;
    }

    let nextPollSeconds = TOKEN_POLL_INTERVAL;
    if (posRes.status === 200) {
      const remaining = posRes.json('data.position');
      const suggestedMs = posRes.json('data.nextPollIntervalMs');
      queueDepth.add(remaining);
      if (suggestedMs && suggestedMs > 0) {
        nextPollSeconds = suggestedMs / 1000;
      }
    }

    sleep(nextPollSeconds);
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
// 주의: VU+iteration 기반 고유 uid → 동일 유저 재진입 없이 큐 크기가 실제로 증가함
function runCase4(data) {
  const iteration = exec.scenario.iterationInTest;
  const pw = 'Password1!';
  let uid;

  if (CASE4_FLOW === 'inline-signup') {
    uid = `${data.prefix}${String(iteration).padStart(5, '0')}`;
    const signupRes = http.post(
      `${BASE_URL}/api/v1/members/signup`,
      JSON.stringify({ memberId: uid, password: pw, name: 'StressUser', email: `${uid}@k6stress.com`, birthDate: '1990-01-01' }),
      { headers: JSON_HEADERS }
    );
    if (!(signupRes.status === 200 || isDuplicateSignup(signupRes))) {
      check(signupRes, { '회원가입 성공/중복 허용': r => r.status === 200 || isDuplicateSignup(r) });
      return;
    }
  } else {
    uid = data.registered[iteration];
    if (!uid) {
      console.error(`[case4] no pre-seeded account for iteration=${iteration}. Increase CASE4_ACCOUNT_COUNT.`);
      return;
    }
  }

  const authHeaders = { ...JSON_HEADERS, 'X-Loopers-LoginId': uid, 'X-Loopers-LoginPw': pw };

  // 대기열 진입
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, { headers: authHeaders });
  const enterOk = check(enterRes, { '진입 성공': r => r.status === 200 });
  if (!enterOk) {
    logEnterFailure('case4', uid, enterRes);
  }

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
