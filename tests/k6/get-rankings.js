import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ── 커스텀 메트릭 ──
const errorRate = new Rate('errors');
const latency = new Trend('request_latency');

// ── 파라미터 ──
// PERIOD: daily | weekly | monthly (기본 daily)
// BASE_URL: http://localhost:8080 (기본)
// TARGET_DATE: yyyyMMdd (기본 20260416)
// SIZE: 페이지 크기 (기본 20)
const PERIOD = __ENV.PERIOD || 'daily';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_DATE = __ENV.TARGET_DATE || '20260416';
const SIZE = __ENV.SIZE || '20';

// ── 부하 설정 (load profile — VUs 100, 30s, stages) ──
export const options = {
  stages: [
    { duration: '9s', target: 50 },    // ramp-up
    { duration: '15s', target: 100 },  // sustain
    { duration: '6s', target: 0 },     // ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    errors: ['rate<0.01'],
  },
  tags: { period: PERIOD },
};

// ── 시나리오 ──
export default function () {
  const url = `${BASE_URL}/api/v1/rankings?period=${PERIOD}&date=${TARGET_DATE}&size=${SIZE}&page=1`;

  const res = http.get(url, {
    headers: { 'Accept': 'application/json' },
    tags: { endpoint: 'GET /api/v1/rankings', period: PERIOD },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
    'body has data field': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.meta && body.meta.result === 'SUCCESS';
      } catch (e) {
        return false;
      }
    },
  });

  errorRate.add(res.status !== 200);
  latency.add(res.timings.duration);

  sleep(1);
}

// ── 실행 요약 (teardown) ──
export function handleSummary(data) {
  const metrics = data.metrics;
  const p95 = metrics.http_req_duration.values['p(95)'].toFixed(2);
  const p99 = metrics.http_req_duration.values['p(99)'].toFixed(2);
  const avg = metrics.http_req_duration.values.avg.toFixed(2);
  const errRate = ((metrics.errors?.values?.rate || 0) * 100).toFixed(3);
  const totalReqs = metrics.http_reqs.values.count;
  const tps = metrics.http_reqs.values.rate.toFixed(2);

  const summary = `
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
부하테스트 — GET /api/v1/rankings?period=${PERIOD}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  VUs:             100 (load profile)
  Duration:        30s (9s ramp + 15s sustain + 6s down)
  Total Requests:  ${totalReqs}
  Effective TPS:   ${tps}
  Error Rate:      ${errRate}%
  Latency:
    - avg: ${avg}ms
    - p95: ${p95}ms
    - p99: ${p99}ms
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
`;

  return {
    stdout: summary,
  };
}
