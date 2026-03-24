import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = 'http://localhost:8080';

// -------------------------
// 환경 설정 (필요 시 변경)
// -------------------------
const PRODUCT_ID = 1;      // 미리 생성해둔 상품 ID
const BRAND_ID = 1;        // 헬스체크용 브랜드 ID (PG 미경유 순수 DB 조회)
const TUPLE_COUNT = 600;   // 총 준비할 (유저, 주문) tuple 수 (20 req/s × 30s)

export const options = {
  setupTimeout: '300s',
  scenarios: {
    payment_load: {
      executor: 'constant-arrival-rate',
      rate: 20,          // 초당 요청 수
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 50,
      maxVUs: 300,       // 타임아웃 없음 + 10초 지연 시 최대 200 스레드 점유 대비
      exec: 'paymentTest',
    },
    health_check: {
      executor: 'constant-arrival-rate',
      rate: 5,           // 초당 5회 — PG 무관 엔드포인트 응답 저하 여부 관찰
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 5,
      maxVUs: 20,
      exec: 'healthCheck',
    },
  },
};

export function setup() {
  const jsonHeaders = { 'Content-Type': 'application/json' };
  const entries = []; // { loginId, password, orderId }

  for (let i = 0; i < TUPLE_COUNT; i++) {
    const loginId = `k6User${i}`;
    const password = 'Test1234!';

    // 1. 유저 생성
    http.post(
      `${BASE_URL}/api/v1/users`,
      JSON.stringify({
        loginId: loginId,
        password: password,
        name: `유저`,
        birthDate: '1990-01-01',
        email: `k6user${i}@test.com`,
      }),
      { headers: jsonHeaders }
    );

    // 2. 주문 1개 생성
    const res = http.post(
      `${BASE_URL}/api/v1/orders`,
      JSON.stringify({
        items: [{ productId: PRODUCT_ID, quantity: 1 }],
        couponId: null,
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Loopers-LoginId': loginId,
          'X-Loopers-LoginPw': password,
        },
      }
    );

    const orderId = res.json('data.orderId');
    if (orderId) {
      entries.push({ loginId, password, orderId });
    }
  }

  console.log(`준비 완료: 유저 ${TUPLE_COUNT}명, 주문 ${entries.length}개`);
  return { entries };
}

export function paymentTest(data) {
  // iterationInTest: 전체 시나리오에서 전역적으로 유일한 카운터 (VU간 중복 없음)
  const entry = data.entries[exec.scenario.iterationInTest % data.entries.length];

  const res = http.post(
    `${BASE_URL}/api/v1/payments`,
    JSON.stringify({
      orderId: entry.orderId,
      cardType: 'SAMSUNG',
      cardNo: '1234-5678-9012-3456',
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Loopers-LoginId': entry.loginId,
        'X-Loopers-LoginPw': entry.password,
      },
    }
  );

  check(res, {
    'payment status is 2xx': (r) => r.status >= 200 && r.status < 300,
    'payment status is not 5xx': (r) => r.status < 500,
  });
}

// PG를 거치지 않는 엔드포인트로 서버 스레드 고갈 여부 관찰
// 결제 지연 중에도 이 요청이 느려지거나 실패하면 스레드 풀 고갈로 판단
export function healthCheck() {
  const res = http.get(`${BASE_URL}/api/v1/brands/${BRAND_ID}`);

  check(res, {
    'health status is 2xx': (r) => r.status >= 200 && r.status < 300,
    'health status is not 5xx': (r) => r.status < 500,
  });
}
