// 랭킹 API 부하 테스트 — 멘토링 피드백 반영 후 검증
//
// 테스트 대상:
//   1. GET /api/v1/rankings (offset 페이징)
//   2. GET /api/v1/rankings/cursor (cursor 페이징)
//   3. GET /api/v1/products/{productId} (View 이벤트 Kafka 직접 발행)
//
// 사전 조건:
//   - Redis ZSET에 대량 데이터 seed 완료 (seed-ranking.js 또는 수동)
//   - commerce-api 서버 실행 중
//
// 실행:
//   k6 run ranking-api-load.js
//   k6 run --env SCENARIO=spike ranking-api-load.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SCENARIO = __ENV.SCENARIO || 'all';

const rankingOffsetDuration = new Trend('ranking_offset_duration', true);
const rankingCursorDuration = new Trend('ranking_cursor_duration', true);
const viewEventDuration = new Trend('view_event_duration', true);
const errorRate = new Rate('error_rate');
const successCount = new Counter('success_count');

export const options = {
  scenarios: {
    // 시나리오 1: offset 기반 랭킹 조회 — 기본 부하
    ranking_offset_base: {
      executor: 'constant-vus',
      vus: 50,
      duration: '60s',
      exec: 'rankingOffsetLoad',
      tags: { scenario: 'offset_base' },
    },
    // 시나리오 2: cursor 기반 랭킹 조회 — 기본 부하
    ranking_cursor_base: {
      executor: 'constant-vus',
      vus: 50,
      duration: '60s',
      exec: 'rankingCursorLoad',
      startTime: '65s',
      tags: { scenario: 'cursor_base' },
    },
    // 시나리오 3: offset 고부하
    ranking_offset_high: {
      executor: 'constant-vus',
      vus: 200,
      duration: '60s',
      exec: 'rankingOffsetLoad',
      startTime: '130s',
      tags: { scenario: 'offset_high' },
    },
    // 시나리오 4: View 이벤트 발행 + 랭킹 조회 동시 (Kafka 직접 발행 검증)
    mixed_view_ranking: {
      executor: 'constant-vus',
      vus: 100,
      duration: '60s',
      exec: 'mixedViewAndRanking',
      startTime: '195s',
      tags: { scenario: 'mixed' },
    },
    // 시나리오 5: 스파이크 테스트
    ranking_spike: {
      executor: 'ramping-vus',
      stages: [
        { duration: '10s', target: 10 },
        { duration: '5s', target: 500 },
        { duration: '30s', target: 500 },
        { duration: '10s', target: 10 },
      ],
      exec: 'rankingOffsetLoad',
      startTime: '260s',
      tags: { scenario: 'spike' },
    },
  },
  thresholds: {
    'ranking_offset_duration{scenario:offset_base}': ['p(95)<100'],
    'ranking_offset_duration{scenario:offset_high}': ['p(95)<200'],
    'ranking_cursor_duration{scenario:cursor_base}': ['p(95)<100'],
    'view_event_duration': ['p(95)<200'],
    'error_rate': ['rate<0.05'],
  },
};

// --- 테스트 함수들 ---

export function rankingOffsetLoad() {
  const page = Math.floor(Math.random() * 5); // 0~4 페이지
  const size = 20;
  const res = http.get(`${BASE_URL}/api/v1/rankings?page=${page}&size=${size}`);

  rankingOffsetDuration.add(res.timings.duration);

  const ok = check(res, {
    'offset status 200': (r) => r.status === 200,
    'offset has items': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.items !== undefined;
      } catch {
        return false;
      }
    },
  });

  errorRate.add(!ok);
  if (ok) successCount.add(1);

  sleep(0.1);
}

export function rankingCursorLoad() {
  // 첫 요청: cursor 없이
  const res1 = http.get(`${BASE_URL}/api/v1/rankings/cursor?size=20`);
  rankingCursorDuration.add(res1.timings.duration);

  let ok1 = check(res1, {
    'cursor status 200': (r) => r.status === 200,
  });

  if (ok1) {
    successCount.add(1);
    try {
      const body = JSON.parse(res1.body);
      if (body.data && body.data.nextCursor) {
        // 두 번째 요청: cursor 사용
        const res2 = http.get(`${BASE_URL}/api/v1/rankings/cursor?cursor=${body.data.nextCursor}&size=20`);
        rankingCursorDuration.add(res2.timings.duration);
        check(res2, { 'cursor page2 status 200': (r) => r.status === 200 });
        successCount.add(1);
      }
    } catch {
      // ignore parse error
    }
  } else {
    errorRate.add(1);
  }
  errorRate.add(!ok1);

  sleep(0.1);
}

export function mixedViewAndRanking() {
  // 50% 확률로 View 이벤트 발행 (상품 조회) vs 랭킹 조회
  if (Math.random() < 0.5) {
    const productId = `k6prod0${Math.floor(Math.random() * 5) + 1}`;
    const memberId = Math.floor(Math.random() * 10000) + 1;
    const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
      headers: { 'X-USER-ID': String(memberId) },
    });
    viewEventDuration.add(res.timings.duration);

    const ok = check(res, {
      'view status 200': (r) => r.status === 200,
    });

    errorRate.add(!ok);
    if (ok) successCount.add(1);
  } else {
    rankingOffsetLoad();
  }

  sleep(0.05);
}

export function handleSummary(data) {
  const scenarios = ['offset_base', 'cursor_base', 'offset_high', 'mixed', 'spike'];
  let summary = '\n=== 랭킹 API 부하 테스트 결과 요약 ===\n\n';

  if (data.metrics.ranking_offset_duration) {
    const m = data.metrics.ranking_offset_duration;
    summary += `[Offset 페이징]\n`;
    summary += `  p50: ${(m.values['p(50)'] || 0).toFixed(2)}ms\n`;
    summary += `  p95: ${(m.values['p(95)'] || 0).toFixed(2)}ms\n`;
    summary += `  p99: ${(m.values['p(99)'] || 0).toFixed(2)}ms\n`;
    summary += `  avg: ${(m.values['avg'] || 0).toFixed(2)}ms\n\n`;
  }

  if (data.metrics.ranking_cursor_duration) {
    const m = data.metrics.ranking_cursor_duration;
    summary += `[Cursor 페이징]\n`;
    summary += `  p50: ${(m.values['p(50)'] || 0).toFixed(2)}ms\n`;
    summary += `  p95: ${(m.values['p(95)'] || 0).toFixed(2)}ms\n`;
    summary += `  p99: ${(m.values['p(99)'] || 0).toFixed(2)}ms\n`;
    summary += `  avg: ${(m.values['avg'] || 0).toFixed(2)}ms\n\n`;
  }

  if (data.metrics.view_event_duration) {
    const m = data.metrics.view_event_duration;
    summary += `[View 이벤트 (Kafka 직접 발행)]\n`;
    summary += `  p50: ${(m.values['p(50)'] || 0).toFixed(2)}ms\n`;
    summary += `  p95: ${(m.values['p(95)'] || 0).toFixed(2)}ms\n`;
    summary += `  p99: ${(m.values['p(99)'] || 0).toFixed(2)}ms\n`;
    summary += `  avg: ${(m.values['avg'] || 0).toFixed(2)}ms\n\n`;
  }

  if (data.metrics.error_rate) {
    summary += `[Error Rate]: ${(data.metrics.error_rate.values.rate * 100).toFixed(2)}%\n`;
  }

  if (data.metrics.success_count) {
    summary += `[Success Count]: ${data.metrics.success_count.values.count}\n`;
  }

  summary += '\n=== 판정 기준 ===\n';
  summary += '  offset p95 < 100ms (기본) / p95 < 200ms (고부하)\n';
  summary += '  cursor p95 < 100ms\n';
  summary += '  view p95 < 200ms (Kafka 직접 발행 = Outbox 대비 개선)\n';
  summary += '  error rate < 5%\n';

  console.log(summary);
  return {};
}
