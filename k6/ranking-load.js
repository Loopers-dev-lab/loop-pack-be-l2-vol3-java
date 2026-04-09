import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * 랭킹 API 부하 테스트
 *
 * 3단계 시나리오로 부하를 증가시키며 응답 시간 변화를 측정한다.
 *
 * 시나리오:
 * - 저부하: 1 VU (15초) — 기준선 확보
 * - 중부하: 10 VU (15초) — 커넥션 풀 경합 확인
 * - 고부하: 50 VU (15초) — 병목 지점 식별
 *
 * 사전 조건:
 * - Redis에 ranking:all:{오늘날짜} ZSET 키가 존재해야 한다 (10만 멤버 권장)
 * - commerce-api가 실행 중이어야 한다
 *
 * 실행 방법:
 *   k6 run k6/ranking-load.js
 */

const BASE_URL = 'http://localhost:8080';

export const options = {
  scenarios: {
    low: {
      executor: 'constant-vus',
      vus: 1,
      duration: '15s',
      startTime: '0s',
      tags: { scenario: 'low_1vu' },
    },
    mid: {
      executor: 'constant-vus',
      vus: 10,
      duration: '15s',
      startTime: '20s',
      tags: { scenario: 'mid_10vu' },
    },
    high: {
      executor: 'constant-vus',
      vus: 50,
      duration: '15s',
      startTime: '40s',
      tags: { scenario: 'high_50vu' },
    },
  },
  thresholds: {
    'http_req_duration{scenario:low_1vu}': ['p(95)<500'],
    'http_req_duration{scenario:mid_10vu}': ['p(95)<500'],
    'http_req_duration{scenario:high_50vu}': ['p(95)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

// 워밍업: JIT + 커넥션 풀 초기화
export function setup() {
  for (let i = 0; i < 50; i++) {
    http.get(`${BASE_URL}/api/v1/rankings?period=daily&page=1&size=20`);
  }
}

export default function () {
  const r = Math.random();

  if (r < 0.6) {
    // 60%: 일간 랭킹 첫 페이지 (가장 빈번한 호출 패턴)
    const res = http.get(`${BASE_URL}/api/v1/rankings?period=daily&page=1&size=20`,
      { tags: { name: 'daily_page1' } });
    check(res, { '200 OK': (r) => r.status === 200 });

  } else if (r < 0.8) {
    // 20%: 일간 랭킹 중간 페이지 (페이지 깊이에 따른 성능 변화 확인)
    const page = Math.floor(Math.random() * 50) + 2;
    const res = http.get(`${BASE_URL}/api/v1/rankings?period=daily&page=${page}&size=20`,
      { tags: { name: 'daily_deep' } });
    check(res, { '200 OK': (r) => r.status === 200 });

  } else {
    // 20%: 시간 랭킹 (hourly 키가 없으면 fallback 또는 빈 응답)
    const res = http.get(`${BASE_URL}/api/v1/rankings?period=hourly&page=1&size=20`,
      { tags: { name: 'hourly_page1' } });
    check(res, { '200 OK': (r) => r.status === 200 });
  }
}
