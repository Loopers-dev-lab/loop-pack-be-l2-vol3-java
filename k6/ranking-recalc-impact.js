// 재집계 중 랭킹 API 응답 시간 영향 측정
//
// 실험 시나리오:
//   Phase 1: 재집계 없이 50VU 60초 조회 (baseline)
//   Phase 2: 재집계 트리거 후 50VU 60초 조회 (recalculation impact)
//
// 사전 조건:
//   - commerce-api 서버 실행 중
//   - Redis ZSET에 대량 데이터 seed 완료
//   - commerce-streamer 실행 중 (재집계 Kafka consumer)
//
// 실행:
//   k6 run ranking-recalc-impact.js
//   k6 run --env PHASE=baseline ranking-recalc-impact.js
//   k6 run --env PHASE=recalc ranking-recalc-impact.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PHASE = __ENV.PHASE || 'all';

const baselineDuration = new Trend('baseline_duration', true);
const recalcDuration = new Trend('recalc_duration', true);
const emptyResponses = new Counter('empty_responses');
const errorRate = new Rate('error_rate');

function buildScenarios() {
  const scenarios = {};

  if (PHASE === 'all' || PHASE === 'baseline') {
    scenarios.baseline = {
      executor: 'constant-vus',
      vus: 50,
      duration: '60s',
      exec: 'baselineQuery',
      tags: { phase: 'baseline' },
    };
  }

  if (PHASE === 'all' || PHASE === 'recalc') {
    scenarios.recalc_query = {
      executor: 'constant-vus',
      vus: 50,
      duration: '60s',
      exec: 'recalcQuery',
      startTime: PHASE === 'all' ? '65s' : '0s',
      tags: { phase: 'recalc' },
    };
  }

  return scenarios;
}

export const options = {
  scenarios: buildScenarios(),
  thresholds: {
    'baseline_duration': ['p(95)<100'],
    'recalc_duration': ['p(95)<200'],
    'error_rate': ['rate<0.05'],
  },
};

export function baselineQuery() {
  const page = Math.floor(Math.random() * 5);
  const res = http.get(`${BASE_URL}/api/v1/rankings?page=${page}&size=20`);

  baselineDuration.add(res.timings.duration);

  const ok = check(res, {
    'baseline status 200': (r) => r.status === 200,
  });

  if (ok) {
    try {
      const body = JSON.parse(res.body);
      if (body.data && body.data.items && body.data.items.length === 0) {
        emptyResponses.add(1);
      }
    } catch {}
  }

  errorRate.add(!ok);
  sleep(0.1);
}

export function recalcQuery() {
  const page = Math.floor(Math.random() * 5);
  const res = http.get(`${BASE_URL}/api/v1/rankings?page=${page}&size=20`);

  recalcDuration.add(res.timings.duration);

  const ok = check(res, {
    'recalc status 200': (r) => r.status === 200,
  });

  if (ok) {
    try {
      const body = JSON.parse(res.body);
      if (body.data && body.data.items && body.data.items.length === 0) {
        emptyResponses.add(1);
      }
    } catch {}
  }

  errorRate.add(!ok);
  sleep(0.1);
}

export function handleSummary(data) {
  let summary = '\n=== 재집계 영향 측정 결과 ===\n\n';

  if (data.metrics.baseline_duration) {
    const m = data.metrics.baseline_duration;
    summary += `[Phase 1 — Baseline (재집계 없음)]\n`;
    summary += `  p50: ${(m.values['p(50)'] || 0).toFixed(2)}ms\n`;
    summary += `  p95: ${(m.values['p(95)'] || 0).toFixed(2)}ms\n`;
    summary += `  p99: ${(m.values['p(99)'] || 0).toFixed(2)}ms\n`;
    summary += `  max: ${(m.values['max'] || 0).toFixed(2)}ms\n`;
    summary += `  count: ${m.values['count'] || 0}\n\n`;
  }

  if (data.metrics.recalc_duration) {
    const m = data.metrics.recalc_duration;
    summary += `[Phase 2 — 재집계 중 조회]\n`;
    summary += `  p50: ${(m.values['p(50)'] || 0).toFixed(2)}ms\n`;
    summary += `  p95: ${(m.values['p(95)'] || 0).toFixed(2)}ms\n`;
    summary += `  p99: ${(m.values['p(99)'] || 0).toFixed(2)}ms\n`;
    summary += `  max: ${(m.values['max'] || 0).toFixed(2)}ms\n`;
    summary += `  count: ${m.values['count'] || 0}\n\n`;
  }

  if (data.metrics.empty_responses) {
    summary += `[빈 응답 횟수]: ${data.metrics.empty_responses.values.count}\n`;
  }

  if (data.metrics.error_rate) {
    summary += `[Error Rate]: ${(data.metrics.error_rate.values.rate * 100).toFixed(2)}%\n`;
  }

  summary += '\n=== 판정 ===\n';
  summary += '  baseline p95 < 100ms, recalc p95 < 200ms\n';
  summary += '  빈 응답 0건 → Shadow ZSET 방식의 무중단 재집계 증명\n';

  console.log(summary);
  return {};
}
