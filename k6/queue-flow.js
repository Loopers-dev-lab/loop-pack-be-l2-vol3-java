/**
 * Week 8 대기열 시스템 E2E 부하 테스트
 *
 * 전제:
 *   k6/seed-users.js 를 먼저 실행하여 k6u1 ~ k6u{VUS} 유저가 생성되어 있어야 한다.
 *   사전 생성된 유저를 사용함으로써 모든 VU가 동시에 대기열에 진입할 수 있다.
 *
 * 검증 시나리오:
 *   1. {VUS}명이 동시에 대기열에 진입
 *   2. 스케줄러가 100ms마다 14명씩 토큰 발급
 *   3. 각 유저가 폴링 → tokenIssued=true 확인
 *   4. 토큰 보유 유저가 주문 API 호출 → 성공
 *   5. 주문 후 토큰 자동 삭제 확인 (재주문 400)
 *
 * 실행 방법:
 *   k6 run k6/seed-users.js          # 최초 1회만
 *   k6 run k6/queue-flow.js
 *   K6_WEB_DASHBOARD=true VUS=100 k6 run k6/queue-flow.js
 *
 * 환경 변수:
 *   BASE_URL   : 서버 주소 (기본: http://localhost:8080)
 *   VUS        : 동시 접속자 수 (기본: 50, 최대: 10000)
 *   PRODUCT_ID : 기존 상품 재사용 (없으면 setup에서 신규 생성)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

// ── 설정 ────────────────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS      = parseInt(__ENV.VUS || '50');

// seed-users.js로 생성한 유저 인증 정보
const SEED_PASSWORD = 'K6seed1234';

// ── 커스텀 메트릭 ────────────────────────────────────────────────────────────
const tokenWaitTimeMs  = new Trend('token_wait_time_ms', true);
const tokenIssuedRate  = new Rate('token_issued_rate');
const orderSuccessRate = new Rate('order_success_rate');
const pollCount        = new Counter('poll_count');

// ── 시나리오 옵션 ────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    queue_flow: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: 1,
      maxDuration: '3m',
    },
  },
  thresholds: {
    // 95%의 유저가 60초 이내에 토큰을 발급받아야 함
    'token_wait_time_ms': ['p(95)<60000'],
    // 토큰 발급률 99% 이상
    'token_issued_rate': ['rate>0.99'],
    // 주문 성공률 99% 이상
    'order_success_rate': ['rate>0.99'],
    // HTTP 에러율 — 재주문 거부(400)는 예상 응답이므로 완화
    'http_req_failed': ['rate<0.15'],
  },
};

// ── setup: 상품 생성 (또는 기존 상품 재사용) ────────────────────────────────
export function setup() {
  if (__ENV.PRODUCT_ID) {
    const productId = parseInt(__ENV.PRODUCT_ID);
    console.log(`[setup] 기존 상품 재사용 — productId=${productId}`);
    return { productId };
  }

  const adminHeaders = {
    'Content-Type': 'application/json',
    'X-Loopers-Ldap': 'loopers.admin',
  };

  const runTs   = Date.now();
  const brandRes = http.post(
    `${BASE_URL}/api-admin/v1/brands`,
    JSON.stringify({ name: `k6queue${runTs}` }),
    { headers: adminHeaders }
  );
  if (!check(brandRes, { '[setup] 브랜드 생성': (r) => r.status === 200 })) {
    throw new Error(`브랜드 생성 실패: ${brandRes.status} ${brandRes.body}`);
  }
  const brandId = JSON.parse(brandRes.body).data.id;

  const productRes = http.post(
    `${BASE_URL}/api-admin/v1/products`,
    JSON.stringify({ brandId, name: `k6queue${runTs}`, price: 10000, stock: VUS + 100 }),
    { headers: adminHeaders }
  );
  if (!check(productRes, { '[setup] 상품 생성': (r) => r.status === 200 })) {
    throw new Error(`상품 생성 실패: ${productRes.status} ${productRes.body}`);
  }
  const productId = JSON.parse(productRes.body).data.id;

  console.log(`[setup] 완료 — brandId=${brandId}, productId=${productId}, stock=${VUS + 100}`);
  return { productId };
}

// ── 메인 시나리오 ─────────────────────────────────────────────────────────────
export default function (data) {
  const { productId } = data;

  // seed-users.js로 생성한 유저 사용 — VU 번호로 직접 매핑
  const loginId = `k6u${__VU}`;
  const userHeaders = {
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': loginId,
    'X-Loopers-LoginPw': SEED_PASSWORD,
  };

  // ── Step 1: 대기열 진입 (사전 생성 유저 → 회원가입 불필요) ─────────────────
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, { headers: userHeaders });
  if (!check(enterRes, { 'Step1 대기열 진입': (r) => r.status === 200 })) {
    console.error(`VU${__VU}(${loginId}) 대기열 진입 실패: ${enterRes.status} ${enterRes.body}`);
    return;
  }

  const enterBody  = JSON.parse(enterRes.body);
  const myPosition = enterBody.data?.position ?? '?';
  console.log(`VU${__VU}: 대기열 진입 완료 — 순번 ${myPosition}번 / 전체 ${enterBody.data?.totalWaiting}명`);

  // ── Step 2: 폴링 — tokenIssued=true 까지 대기 ──────────────────────────────
  const waitStart = Date.now();
  let   issued    = false;
  const maxWaitMs = 120_000; // 최대 2분

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
        console.log(`VU${__VU}: 토큰 발급 확인 — ${(waitedMs / 1000).toFixed(1)}초 대기`);
        break;
      }

      // 서버 권장 폴링 주기 사용
      const intervalSec = (pos?.recommendedPollingIntervalMs ?? 1000) / 1000;
      sleep(intervalSec);
    } else {
      console.warn(`VU${__VU}: position 조회 실패 ${posRes.status}`);
      sleep(1);
    }
  }

  if (!issued) {
    tokenIssuedRate.add(0);
    console.error(`VU${__VU}: 토큰 발급 타임아웃`);
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
    console.error(`VU${__VU}: 주문 실패 — ${orderRes.status}: ${orderRes.body}`);
    return;
  }
  console.log(`VU${__VU}: 주문 완료 — orderId=${JSON.parse(orderRes.body).data?.orderId}`);

  // ── Step 4: 토큰 삭제 확인 — 재주문 시 400이어야 함 ───────────────────────
  sleep(0.5); // AFTER_COMMIT @Async 처리 대기

  const reorderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ items: [{ productId, quantity: 1 }], userCouponId: null }),
    { headers: userHeaders }
  );
  check(reorderRes, {
    'Step4 토큰 삭제 확인 (재주문 거부 400)': (r) => r.status === 400,
  });

  if (reorderRes.status !== 400) {
    console.warn(`VU${__VU}: 재주문 거부 실패 (status=${reorderRes.status})`);
  }
}

// ── teardown ──────────────────────────────────────────────────────────────────
export function teardown(data) {
  console.log(`[teardown] 테스트 완료. productId=${data?.productId}`);
}
