/**
 * 테스트 1: Redis 장애 시 DB fallback 서빙 검증
 *
 * 시나리오:
 *   Stage 1 (30s) — 정상 상태에서 랭킹 API baseline 측정
 *   Stage 2 (수동) — Redis 컨테이너 stop (테스트 중 수동 실행)
 *   Stage 3 (30s) — Redis 장애 상태에서 DB fallback으로 정상 응답 확인
 *   Stage 4 (수동) — Redis 컨테이너 restart
 *   Stage 5 (30s) — Redis 복구 후 자동 복귀 확인
 *
 * 실행 방법:
 *   1. docker compose로 인프라 + 앱 기동
 *   2. bucket 테이블에 테스트 데이터 INSERT (setup 참고)
 *   3. k6 run k6/ranking/01-redis-fallback.js
 *   4. Stage 1 끝나면 (30초 후): docker stop redis-master redis-readonly
 *   5. Stage 3 끝나면 (60초 후): docker start redis-master redis-readonly
 *   6. 결과 확인: fallback 구간에서도 200 응답 + 상품 데이터 반환
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const rankingSuccess = new Rate('ranking_success');
const rankingDuration = new Trend('ranking_duration', true);
const fallbackDetected = new Counter('fallback_detected');
const emptyResponse = new Counter('empty_response');

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-vus',
      vus: 10,
      duration: '90s',
    },
  },
  thresholds: {
    ranking_success: ['rate>0.95'],
    ranking_duration: ['p(95)<3000'],
  },
};

export default function () {
  const today = new Date().toISOString().slice(0, 10).replace(/-/g, '');

  const res = http.get(`${BASE_URL}/api/v1/rankings?period=DAILY&date=${today}&page=0&size=20`);

  const isSuccess = res.status === 200;
  rankingSuccess.add(isSuccess);
  rankingDuration.add(res.timings.duration);

  if (isSuccess) {
    const body = JSON.parse(res.body);
    const items = body.data.items;

    if (items.length === 0) {
      emptyResponse.add(1);
    }

    // 상품 정보가 조합되어 반환되는지 (fallback이든 Redis든)
    check(res, {
      'status 200': (r) => r.status === 200,
      'has items or empty (both valid)': () => items !== undefined,
      'items have productName': () => items.length === 0 || items[0].productName !== undefined,
      'items have score': () => items.length === 0 || items[0].score !== undefined,
    });
  }

  sleep(0.5);
}

export function handleSummary(data) {
  const total = data.metrics.ranking_success ? data.metrics.ranking_success.values.passes + data.metrics.ranking_success.values.fails : 0;
  const successRate = data.metrics.ranking_success ? data.metrics.ranking_success.values.rate : 0;
  const p95 = data.metrics.ranking_duration ? data.metrics.ranking_duration.values['p(95)'] : 0;
  const fallbacks = data.metrics.fallback_detected ? data.metrics.fallback_detected.values.count : 0;
  const empties = data.metrics.empty_response ? data.metrics.empty_response.values.count : 0;

  return {
    stdout: `
=== Redis Fallback 테스트 결과 ===
총 요청: ${total}
성공률: ${(successRate * 100).toFixed(1)}%
p95 응답시간: ${p95.toFixed(0)}ms
빈 응답 수: ${empties}

판정:
  Redis 정상 구간: 200 응답 + 상품 데이터
  Redis 장애 구간: 200 응답 + DB fallback (응답시간 증가 예상)
  Redis 복구 구간: 200 응답 + 정상 복귀

  성공률 > 95% 이면 PASS (Redis 장애에도 서빙 가능)
====================================
`,
  };
}
