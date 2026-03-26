import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE_URL = 'http://localhost:8080';

// -------------------------
// 환경 설정 (실행 전 채워주세요)
// -------------------------
const USER_COUNT = 100;                  // 생성할 유저 수 (= 좋아요/주문 이벤트 수)
const PRODUCT_STOCK = 99999;             // 주문 시나리오 재고 소진 방지

export const options = {
  setupTimeout: '120s',
  scenarios: {
    like_pipeline: {
      executor: 'shared-iterations',
      vus: 10,
      iterations: USER_COUNT,
      exec: 'likePipeline',
    },
    order_pipeline: {
      executor: 'shared-iterations',
      vus: 10,
      iterations: USER_COUNT,
      exec: 'orderPipeline',
    },
  },
};

export function setup() {
  const jsonHeaders = { 'Content-Type': 'application/json' };
  const adminHeaders = {
    'Content-Type': 'application/json',
    'X-Loopers-Ldap': 'loopers.admin'
  };

  // 1. 브랜드 생성
  const brandRes = http.post(
    `${BASE_URL}/api-admin/v1/brands`,
    JSON.stringify({ name: 'k6-test-brand', description: 'k6 이벤트 파이프라인 테스트용 브랜드' }),
    { headers: adminHeaders }
  );
  const brandId = '1';
  if (!brandId) {
    console.error(`브랜드 생성 실패: ${brandRes.body}`);
  }

  // 2. 상품 생성
  const productRes = http.post(
    `${BASE_URL}/api-admin/v1/products`,
    JSON.stringify({
      name: 'k6-test-product',
      description: 'k6 이벤트 파이프라인 테스트용 상품',
      stock: PRODUCT_STOCK,
      price: 10000,
      brandId: brandId,
    }),
    { headers: adminHeaders }
  );
  const productId = '1';
  if (!productId) {
    console.error(`상품 생성 실패: ${productRes.body}`);
  }

  // 3. 유저 생성
  const users = [];
  for (let i = 0; i < USER_COUNT; i++) {
    const loginId = `k6EventUser${i}`;
    const password = 'Test1234!';

    http.post(
      `${BASE_URL}/api/v1/users`,
      JSON.stringify({
        loginId: loginId,
        password: password,
        name: `유저${i}`,
        birthDate: '1990-01-01',
        email: `k6event${i}@test.com`,
      }),
      { headers: jsonHeaders }
    );

    users.push({ loginId, password });
  }

  console.log(`준비 완료 — 브랜드: ${brandId}, 상품: ${productId}, 유저: ${users.length}명`);
  return { users, productId };
}

// catalog-events 발행 검증
// 각 유저가 상품에 좋아요 → OutboxEvent 저장 → Kafka catalog-events 발행
export function likePipeline(data) {
  const user = data.users[exec.scenario.iterationInTest % data.users.length];

  const res = http.post(
    `${BASE_URL}/api/v1/products/${data.productId}/likes`,
    null,
    {
      headers: {
        'X-Loopers-LoginId': user.loginId,
        'X-Loopers-LoginPw': user.password,
      },
    }
  );

  check(res, {
    'like: status is 200': (r) => r.status === 200,
    'like: status is not 5xx': (r) => r.status < 500,
  });
}

// order-events 발행 검증
// 각 유저가 상품 주문 → OutboxEvent 저장 → Kafka order-events 발행
export function orderPipeline(data) {
  const user = data.users[exec.scenario.iterationInTest % data.users.length];

  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      items: [{ productId: data.productId, quantity: 1 }],
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
    'order: status is 201': (r) => r.status === 201,
    'order: status is not 5xx': (r) => r.status < 500,
  });
}
