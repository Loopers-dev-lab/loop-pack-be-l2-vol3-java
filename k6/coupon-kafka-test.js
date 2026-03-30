import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = 'http://localhost:8080';

// -------------------------
// 환경 설정 (필요 시 변경)
// -------------------------
const COUPON_QUANTITY = 100;  // 선착순 발급 가능 수량
const USER_COUNT = 200;       // 총 요청 유저 수 (수량 초과로 100명은 FAILED 예상)

// rate × duration = 총 요청 수
// 20 req/s × 10s = 200건 → DB 커넥션 풀(40) 안에서 처리 가능
const RATE = 20;              // 초당 요청 수 (로컬 환경에서 connection-timeout 회피)
const DURATION = '10s';

export const options = {
  setupTimeout: '120s',
  scenarios: {
    coupon_issue: {
      executor: 'per-vu-iterations',
      vus: 200,        // VU 200개 동시 실행
      iterations: 1,   // 각 VU가 딱 1번만 요청
      maxDuration: '2m',
      exec: 'issueAsync',
    },
  },
};

export function setup() {
  const jsonHeaders = { 'Content-Type': 'application/json' };
  const adminHeaders = {
    'Content-Type': 'application/json',
    'X-Loopers-Ldap': 'loopers.admin',
  };

  // 1. 선착순 쿠폰 생성
  const couponRes = http.post(
    `${BASE_URL}/api-admin/v1/coupons`,
    JSON.stringify({
      name: 'k6-선착순쿠폰',
      type: 'FIXED',
      value: 1000,
      totalQuantity: COUPON_QUANTITY,
      expiredAt: '2099-12-31T23:59:59+09:00',
    }),
    { headers: adminHeaders }
  );

  const couponId = couponRes.json('data.couponId');
  if (!couponId) {
    console.error(`쿠폰 생성 실패: ${couponRes.body}`);
  }

  // 2. 유저 생성
  const users = [];
  for (let i = 0; i < USER_COUNT; i++) {
    const loginId = `k6CouponUser${i}`;
    const password = 'Test1234!';

    http.post(
      `${BASE_URL}/api/v1/users`,
      JSON.stringify({
        loginId,
        password,
        name: `유저${i}`,
        birthDate: '1990-01-01',
        email: `k6coupon${i}@test.com`,
      }),
      { headers: jsonHeaders }
    );

    users.push({ loginId, password });
  }

  console.log(`준비 완료 — 쿠폰 ID: ${couponId} (수량: ${COUPON_QUANTITY}), 유저: ${users.length}명`);
  return { users, couponId };
}

export function issueAsync(data) {
  const user = data.users[exec.scenario.iterationInTest % data.users.length];

  // 비동기 발급 요청 → 202 Accepted + requestId 반환
  const res = http.post(
    `${BASE_URL}/api/v1/coupons/${data.couponId}/issue-async`,
    null,
    {
      headers: {
        'X-Loopers-LoginId': user.loginId,
        'X-Loopers-LoginPw': user.password,
      },
    }
  );

  check(res, {
    'issue-async: 202 Accepted': (r) => r.status === 202,
    'issue-async: requestId 존재': (r) => !!r.json('data.requestId'),
  });

  // 발급 결과 polling (최대 30초, 1초 간격)
  const requestId = res.json('data.requestId');
  if (!requestId) return;

  let finalStatus = 'PENDING';
  for (let i = 0; i < 10 && finalStatus === 'PENDING'; i++) {
    sleep(1);
    const statusRes = http.get(
      `${BASE_URL}/api/v1/coupons/issue-requests/${requestId}`,
      {
        headers: {
          'X-Loopers-LoginId': user.loginId,
          'X-Loopers-LoginPw': user.password,
        },
      }
    );
    if (statusRes.status === 200) {
      finalStatus = statusRes.json('data.status');
    }
  }

  check({ status: finalStatus }, {
    'polling: SUCCESS 또는 FAILED 로 처리 완료': (s) => s.status === 'SUCCESS' || s.status === 'FAILED',
  });
}
