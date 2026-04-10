/**
 * 인증 캐시 워밍업 스크립트
 *
 * 목적:
 *   queue-flow.js / queue-staged-load.js 실행 전 1회 실행.
 *   k6u1~k6uN 각 유저로 인증 요청 1회 → Redis auth:cache:{loginId} 적재.
 *   이후 본 테스트에서는 AuthInterceptor가 DB 조회 없이 Redis만 조회하여
 *   HikariCP 고갈로 인한 대기열 진입 실패를 방지한다.
 *
 * 실행:
 *   k6 run k6/warmup-auth.js                       # 기본 10000명
 *   k6 run k6/warmup-auth.js -e TOTAL=3500          # staged-load 최소 요건
 *   k6 run k6/warmup-auth.js -e WARMUP_VUS=100      # VU 수 조정
 */

import http from 'k6/http';
import { check } from 'k6';

const BASE_URL     = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL        = parseInt(__ENV.TOTAL || '10000');
const VUS          = parseInt(__ENV.WARMUP_VUS || '50');
const ITERS_PER_VU = Math.ceil(TOTAL / VUS);

export const options = {
  scenarios: {
    warmup: {
      executor: 'per-vu-iterations',
      vus: VUS,
      iterations: ITERS_PER_VU,
      maxDuration: '10m',
    },
  },
  thresholds: {
    // 인증 캐시 적재 성공률 — http_req_failed는 제외 (대기열 없는 유저에게 404 정상 반환)
    'checks{check:인증 캐시 적재}': ['rate>0.99'],
  },
};

export default function () {
  const idx = (__VU - 1) * ITERS_PER_VU + __ITER + 1;
  if (idx > TOTAL) return;

  const loginId = `k6u${idx}`;

  // GET /api/v1/queue/position — 인증이 필요한 가벼운 읽기 요청
  // 실제 응답 내용은 무관 (auth:cache:{loginId} 적재가 목적)
  const res = http.get(`${BASE_URL}/api/v1/queue/position`, {
    headers: {
      'X-Loopers-LoginId': loginId,
      'X-Loopers-LoginPw': 'K6seed1234',
    },
  });

  // 200(대기중) / 204(큐에 없음) 모두 캐시 적재 성공
  const ok = check(res, {
    '인증 캐시 적재': (r) => r.status === 200 || r.status === 204 || r.status === 404,
  });

  if (!ok) {
    console.error(`[warmup] k6u${idx} 실패: ${res.status} ${res.body}`);
  }
}

export function teardown() {
  console.log(`[warmup] 완료 — k6u1~k6u${TOTAL} 인증 캐시 적재`);
}
