/**
 * 시나리오 2: 랭킹 API 동시 조회
 *
 * 목적:
 *   일간/시간별 랭킹 API의 Redis 읽기 경로 레이턴시 측정.
 *   DB IN 쿼리 병목 여부 확인 (캐싱 리팩토링 전/후 비교용).
 *
 * 전제:
 *   - commerce-api 실행 중이어야 함
 *   - Redis에 랭킹 데이터가 있어야 함 (scenario1 또는 RankingSyncScheduler 실행 후)
 *     → 데이터가 없으면 빈 목록 반환 (200 OK) — 레이턴시 측정은 가능
 *
 * 실행:
 *   k6 run k6/scenario2-ranking-read.js
 *   k6 run k6/scenario2-ranking-read.js -e VUS=200 -e DURATION=120s
 *
 * 환경 변수:
 *   VUS       : 동시 접속자 수 (기본: 100)
 *   DURATION  : 테스트 지속 시간 (기본: 60s)
 *   SIZE      : 랭킹 조회 건수 (기본: 20)
 *   HOUR      : 시간별 랭킹 조회 시간 (기본: 현재 시각, 0~23)
 */

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS      = parseInt(__ENV.VUS      || '100');
const DURATION = __ENV.DURATION || '60s';
const SIZE     = parseInt(__ENV.SIZE     || '20');
const HOUR     = __ENV.HOUR !== undefined ? parseInt(__ENV.HOUR) : new Date().getHours();

const dailyResponseTime  = new Trend('daily_ranking_response_time_ms', true);
const hourlyResponseTime = new Trend('hourly_ranking_response_time_ms', true);
const dailySuccessRate   = new Rate('daily_ranking_success_rate');
const hourlySuccessRate  = new Rate('hourly_ranking_success_rate');

export const options = {
  scenarios: {
    daily_read: {
      executor: 'constant-vus',
      vus: Math.floor(VUS / 2),
      duration: DURATION,
      exec: 'readDailyRanking',
    },
    hourly_read: {
      executor: 'constant-vus',
      vus: Math.floor(VUS / 2),
      duration: DURATION,
      exec: 'readHourlyRanking',
    },
  },
  thresholds: {
    'daily_ranking_success_rate':        ['rate>0.999'],
    'hourly_ranking_success_rate':       ['rate>0.999'],
    'daily_ranking_response_time_ms':    ['p(95)<50', 'p(99)<100'],
    'hourly_ranking_response_time_ms':   ['p(95)<50', 'p(99)<100'],
    'http_req_failed':                   ['rate<0.001'],
  },
};

export function readDailyRanking() {
  const res = http.get(`${BASE_URL}/api/v1/rankings?size=${SIZE}`);

  const ok = check(res, {
    'daily status 200':   (r) => r.status === 200,
    'daily has data':     (r) => JSON.parse(r.body).data !== undefined,
    'daily meta SUCCESS': (r) => JSON.parse(r.body).meta?.result === 'SUCCESS',
  });

  dailySuccessRate.add(ok ? 1 : 0);
  dailyResponseTime.add(res.timings.duration);

  if (!ok) {
    console.warn(`일간 랭킹 조회 실패: ${res.status} ${res.body}`);
  }
}

export function readHourlyRanking() {
  const res = http.get(`${BASE_URL}/api/v1/rankings/hourly?hour=${HOUR}&size=${SIZE}`);

  const ok = check(res, {
    'hourly status 200':   (r) => r.status === 200,
    'hourly has data':     (r) => JSON.parse(r.body).data !== undefined,
    'hourly meta SUCCESS': (r) => JSON.parse(r.body).meta?.result === 'SUCCESS',
  });

  hourlySuccessRate.add(ok ? 1 : 0);
  hourlyResponseTime.add(res.timings.duration);

  if (!ok) {
    console.warn(`시간별 랭킹 조회 실패: ${res.status} ${res.body}`);
  }
}
