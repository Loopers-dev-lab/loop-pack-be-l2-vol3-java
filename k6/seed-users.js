/**
 * 대기열 테스트용 유저 사전 생성 스크립트
 *
 * 목적:
 *   queue-flow.js / queue-staged-load.js 실행 전 1회 실행하여
 *   k6u1 ~ k6u10000 유저를 미리 생성해둔다.
 *   이 덕분에 실제 부하 테스트에서 회원가입 없이 바로 대기열에 진입할 수 있어
 *   모든 VU가 동시에 대기열에 진입하는 시나리오가 가능하다.
 *
 * 유저 명세:
 *   loginId : k6u1 ~ k6u10000
 *   password: K6seed1234
 *
 * 실행:
 *   k6 run k6/seed-users.js
 *   k6 run k6/seed-users.js -e TOTAL=3500   # 소규모 (staged-load 최소 요건)
 *
 * 소요 시간 (서버 응답 100ms 기준):
 *   200 VUs × 50회 = 10000명 → 약 25~30초
 */

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL        = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL           = parseInt(__ENV.TOTAL || '10000');
const VUS             = parseInt(__ENV.SEED_VUS || '50');
const ITERS_PER_VU    = Math.ceil(TOTAL / VUS);

export const options = {
  scenarios: {
    seed: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERS_PER_VU,
      maxDuration: '5m',
    },
  },
  // 생성 실패는 허용 (이미 존재하면 409 — 재실행 대비)
  thresholds: {},
};

export default function () {
  // VU 1~200, ITER 0~49 → idx 1~10000
  const idx = (__VU - 1) * ITERS_PER_VU + __ITER + 1;
  if (idx > TOTAL) return;

  const loginId = `k6u${idx}`;

  const res = http.post(
    `${BASE_URL}/api/v1/users/signup`,
    JSON.stringify({
      loginId,
      password: 'K6seed1234',
      name:     `k6user${idx}`,
      birthday: '1990-01-01',
      email:    `${loginId}@k6seed.com`,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  // 200(신규) 또는 409(이미 존재) 모두 성공으로 처리
  check(res, { '유저 생성(200 or 409)': (r) => r.status === 200 || r.status === 409 });

  if (res.status !== 200 && res.status !== 409) {
    console.error(`[seed] k6u${idx} 생성 실패: ${res.status} ${res.body}`);
  }
}

export function teardown() {
  console.log(`[seed] 완료 — k6u1 ~ k6u${TOTAL} 생성/확인 완료`);
}
