import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = 'http://localhost:8080';

// -------------------------
// 환경 설정 (필요 시 변경)
// -------------------------
const USER_COUNT = 500;    // 테스트에 사용할 유저 수
const PRODUCT_STOCK = 100000; // 테스트 중 재고 소진 방지용 충분한 재고
const ADMIN_HEADERS = {
  'Content-Type': 'application/json',
  'X-Loopers-Ldap': 'loopers.admin',
};

/**
 * 처리량 측정 목적:
 *   POST /api/v1/orders 평균 처리 시간을 측정해 아래 공식에 대입
 *
 *   이론적 최대 TPS = 커넥션 풀(40) / 평균 처리 시간(초)
 *   안전 TPS        = 이론적 최대 TPS × 0.7
 *   MAX_TOKEN_COUNT = 안전 TPS
 *   batch_size      = MAX_TOKEN_COUNT / (token_ttl_seconds / scheduler_interval_seconds)
 *
 * 시나리오:
 *   - 낮은 부하(warm-up)에서 안정적인 평균 처리 시간을 먼저 측정
 *   - 이후 점진적으로 부하를 높여 포화 지점(p95 급등) 확인
 */
export const options = {
  setupTimeout: '300s',
  scenarios: {
    // Stage 1: 워밍업 — 낮은 부하에서 baseline 평균 처리 시간 측정
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 10,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 20,
      maxVUs: 50,
      exec: 'orderTest',
      startTime: '0s',
    },
    // Stage 2: 중간 부하 — TPS 공식 입력값 수집
    moderate_load: {
      executor: 'constant-arrival-rate',
      rate: 30,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 50,
      maxVUs: 100,
      exec: 'orderTest',
      startTime: '35s',
    },
    // Stage 3: 높은 부하 — 포화 시작 지점 관찰
    high_load: {
      executor: 'constant-arrival-rate',
      rate: 60,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 80,
      maxVUs: 200,
      exec: 'orderTest',
      startTime: '70s',
    },
    // Stage 4: 극한 부하 — 완전 포화 및 p95 급등 확인
    peak_load: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 120,
      maxVUs: 300,
      exec: 'orderTest',
      startTime: '105s',
    },
  },
  thresholds: {
    // 임계값 — 넘어가면 포화 구간으로 판단
    'http_req_duration{scenario:warmup}': ['p(95)<500'],
    'http_req_duration{scenario:moderate_load}': ['p(95)<1000'],
    'http_req_failed': ['rate<0.05'],
  },
};

export function setup() {
  const jsonHeaders = { 'Content-Type': 'application/json' };

  // 1. 브랜드 등록
  http.post(
    `${BASE_URL}/api-admin/v1/brands`,
    JSON.stringify({ name: 'TPS테스트브랜드', description: 'k6 부하 테스트용' }),
    { headers: ADMIN_HEADERS }
  );
  console.log('브랜드 등록 완료 (brandId: 1)');

  // 2. 상품 등록 (재고 충분히)
  http.post(
    `${BASE_URL}/api-admin/v1/products`,
    JSON.stringify({
      name: 'TPS테스트상품',
      description: 'k6 부하 테스트용',
      stock: PRODUCT_STOCK,
      price: 10000,
      brandId: 1,
    }),
    { headers: ADMIN_HEADERS }
  );
  console.log(`상품 등록 완료 (productId: 1, stock: ${PRODUCT_STOCK})`);

  // 3. 유저 생성
  const users = [];
  console.log(`유저 ${USER_COUNT}명 생성 시작...`);

  for (let i = 0; i < USER_COUNT; i++) {
    const loginId = `tpsUser${i}`;
    const password = 'Test1234!';

    http.post(
      `${BASE_URL}/api/v1/users`,
      JSON.stringify({
        loginId: loginId,
        password: password,
        name: `테스트${i}`,
        birthDate: '1990-01-01',
        email: `tpsuser${i}@test.com`,
      }),
      { headers: jsonHeaders }
    );

    users.push({ loginId, password });
  }

  console.log(`유저 ${USER_COUNT}명 생성 완료`);
  return { users };
}

export function orderTest(data) {
  const user = data.users[exec.scenario.iterationInTest % data.users.length];

  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      items: [{ productId: 1, quantity: 1 }],
      couponId: null,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Loopers-LoginId': user.loginId,
        'X-Loopers-LoginPw': user.password,
      },
    }
  );

  check(res, {
    'order status is 2xx': (r) => r.status >= 200 && r.status < 300,
    'order status is not 5xx': (r) => r.status < 500,
  });
}
