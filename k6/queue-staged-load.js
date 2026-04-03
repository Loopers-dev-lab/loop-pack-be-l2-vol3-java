/**
 * Week 8 대기열 단계적 부하 테스트 (500 → 1000 → 2000명)
 *
 * 전제:
 *   k6/seed-users.js 를 먼저 실행하여 k6u1 ~ k6u3600 유저가 생성되어 있어야 한다.
 *   (500 + 1000 + 2000 + 여유분)
 *
 * Wave별 유저 범위 (겹치지 않게 오프셋 적용):
 *   wave_500  : k6u1    ~ k6u500    (OFFSET=0)
 *   wave_1000 : k6u501  ~ k6u1500   (OFFSET=500)
 *   wave_2000 : k6u1501 ~ k6u3500   (OFFSET=1500)
 *
 * 목적:
 *   부하가 증가할수록 대기열, 토큰 발급, 주문 처리가 어떻게 변화하는지 관찰
 *   Wave별 token_wait_time, 주문 성공률 비교 → 시스템 한계점(병목) 식별
 *
 * Wave 구성:
 *   Wave 1 (500명):  0s 시작   — 기준 성능 측정
 *   Wave 2 (1000명): 3m 시작   — 2배 부하
 *   Wave 3 (2000명): 9m 시작   — 4배 부하
 *
 * 실행:
 *   k6 run k6/seed-users.js -e TOTAL=3600   # 최초 1회
 *   k6 run k6/queue-staged-load.js
 *   K6_WEB_DASHBOARD=true k6 run k6/queue-staged-load.js
 *
 * 결과 해석:
 *   wave가 커질수록 token_wait_time이 선형 증가하면 정상 (배치 크기 제약)
 *   주문 성공률이 떨어지면 DB 또는 서버 병목
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE_URL      = __ENV.BASE_URL || 'http://localhost:8080';
const SEED_PASSWORD = 'K6seed1234';

// ── 커스텀 메트릭 ────────────────────────────────────────────────────────────
const tokenWaitTimeMs  = new Trend('token_wait_time_ms',  true);
const tokenIssuedRate  = new Rate('token_issued_rate');
const orderSuccessRate = new Rate('order_success_rate');
const pollCount        = new Counter('poll_count');

// ── 시나리오 설정 ────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    wave_500: {
      executor: 'per-vu-iterations',
      vus: 500,
      iterations: 1,
      startTime: '0s',
      maxDuration: '3m',
      env: { WAVE: '500', OFFSET: '0' },
    },
    wave_1000: {
      executor: 'per-vu-iterations',
      vus: 1000,
      iterations: 1,
      startTime: '3m',
      maxDuration: '5m',
      env: { WAVE: '1000', OFFSET: '500' },
    },
    wave_2000: {
      executor: 'per-vu-iterations',
      vus: 2000,
      iterations: 1,
      startTime: '9m',
      maxDuration: '8m',
      env: { WAVE: '2000', OFFSET: '1500' },
    },
  },
  thresholds: {
    // 전체 token_wait_time 95th percentile < 2분
    'token_wait_time_ms':  ['p(95)<120000'],
    // 토큰 발급률 99% 이상
    'token_issued_rate':   ['rate>0.99'],
    // 주문 성공률 99% 이상
    'order_success_rate':  ['rate>0.99'],
    // HTTP 에러율 — 재주문 거부(400) 포함으로 완화
    'http_req_failed':     ['rate<0.20'],
  },
};

// ── setup: 브랜드 + 상품 생성 ─────────────────────────────────────────────────
export function setup() {
  const adminHeaders = {
    'Content-Type': 'application/json',
    'X-Loopers-Ldap': 'loopers.admin',
  };

  // 실행마다 고유한 브랜드명 — 이전 실행 잔여 데이터와 충돌 방지
  const runTs   = Date.now();
  const brandRes = http.post(
    `${BASE_URL}/api-admin/v1/brands`,
    JSON.stringify({ name: `k6staged${runTs}` }),
    { headers: adminHeaders }
  );
  if (!check(brandRes, { '[setup] 브랜드 생성': (r) => r.status === 200 })) {
    throw new Error(`브랜드 생성 실패: ${brandRes.status} ${brandRes.body}`);
  }
  const brandId = JSON.parse(brandRes.body).data.id;

  // 전체 3파 합산 3500명 + 여유분
  const totalStock = 500 + 1000 + 2000 + 100;
  const productRes = http.post(
    `${BASE_URL}/api-admin/v1/products`,
    JSON.stringify({ brandId, name: `k6staged${runTs}`, price: 10000, stock: totalStock }),
    { headers: adminHeaders }
  );
  if (!check(productRes, { '[setup] 상품 생성': (r) => r.status === 200 })) {
    throw new Error(`상품 생성 실패: ${productRes.status} ${productRes.body}`);
  }
  const productId = JSON.parse(productRes.body).data.id;

  console.log(`[setup] 완료 — brandId=${brandId}, productId=${productId}, stock=${totalStock}`);
  return { productId };
}

// ── 공통 유저 플로우 ──────────────────────────────────────────────────────────
export default function (data) {
  const { productId } = data;
  const wave   = __ENV.WAVE;
  const offset = parseInt(__ENV.OFFSET || '0');

  // wave별 겹치지 않는 유저 범위: k6u{offset + __VU}
  const loginId   = `k6u${offset + __VU}`;
  const userHeaders = {
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': loginId,
    'X-Loopers-LoginPw': SEED_PASSWORD,
  };

  // ── Step 1: 대기열 진입 ────────────────────────────────────────────────────
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, { headers: userHeaders });
  if (!check(enterRes, { 'Step1 대기열 진입': (r) => r.status === 200 })) {
    console.error(`[Wave${wave} ${loginId}] 대기열 진입 실패: ${enterRes.status}`);
    return;
  }
  const enterData = JSON.parse(enterRes.body).data;
  console.log(`[Wave${wave} ${loginId}] 순번 ${enterData?.position}/${enterData?.totalWaiting}`);

  // ── Step 2: 토큰 발급까지 폴링 ────────────────────────────────────────────
  const waitStart = Date.now();
  let   issued    = false;
  const maxWaitMs = 180_000; // 최대 3분 (wave_2000 대비)

  while (Date.now() - waitStart < maxWaitMs) {
    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, { headers: userHeaders });
    pollCount.add(1);

    if (posRes.status === 200) {
      const pos = JSON.parse(posRes.body).data;

      if (pos?.tokenIssued) {
        issued = true;
        const waitedMs = Date.now() - waitStart;
        tokenWaitTimeMs.add(waitedMs);
        tokenIssuedRate.add(1);
        console.log(`[Wave${wave} ${loginId}] 토큰 발급 — ${(waitedMs / 1000).toFixed(1)}초 대기`);
        break;
      }

      const intervalSec = (pos?.recommendedPollingIntervalMs ?? 1000) / 1000;
      sleep(intervalSec);
    } else {
      sleep(1);
    }
  }

  if (!issued) {
    tokenIssuedRate.add(0);
    console.error(`[Wave${wave} ${loginId}] 토큰 발급 타임아웃`);
    return;
  }

  // ── Step 3: 주문 ──────────────────────────────────────────────────────────
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ items: [{ productId, quantity: 1 }], userCouponId: null }),
    { headers: userHeaders }
  );
  const orderOk = check(orderRes, { 'Step3 주문 성공': (r) => r.status === 200 });
  orderSuccessRate.add(orderOk ? 1 : 0);

  if (!orderOk) {
    console.error(`[Wave${wave} ${loginId}] 주문 실패: ${orderRes.status} ${orderRes.body}`);
    return;
  }
  console.log(`[Wave${wave} ${loginId}] 주문 완료 orderId=${JSON.parse(orderRes.body).data?.orderId}`);

  // ── Step 4: 재주문 거부 확인 (토큰 삭제 됐는지) ────────────────────────────
  sleep(0.5); // AFTER_COMMIT @Async 처리 대기

  const reorderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ items: [{ productId, quantity: 1 }], userCouponId: null }),
    { headers: userHeaders }
  );
  check(reorderRes, { 'Step4 토큰 삭제 확인 (재주문 거부 400)': (r) => r.status === 400 });
}

export function teardown() {
  console.log('[teardown] 3 Wave 부하 테스트 완료');
}
