import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

/**
 * 대기열 없이 직접 주문 — DB 한계점 측정
 *
 * Little's Law: M = λ × W
 *   M = HikariCP max-pool-size = 40
 *   W = 주문 1건 평균 처리 시간 (측정 대상)
 *   λ_max = M / W (이론적 최대 TPS)
 *   λ_safe = λ_max × 0.7 (안전 마진 30%)
 *
 * 단계별 VU: 100 → 500 → 1000 → 5000 → 10000
 * queue.enabled=false 상태에서 실행 필요
 */

const successRate = new Rate('success_rate');
const orderDuration = new Trend('order_duration_ms', true);
const errorCount = new Counter('error_count');
const timeoutCount = new Counter('timeout_count');

export const options = {
  stages: [
    // Phase 1: Warm-up
    { duration: '10s', target: 100 },
    { duration: '20s', target: 100 },

    // Phase 2: 500 VU
    { duration: '10s', target: 500 },
    { duration: '20s', target: 500 },

    // Phase 3: 1000 VU
    { duration: '10s', target: 1000 },
    { duration: '20s', target: 1000 },

    // Phase 4: 5000 VU
    { duration: '10s', target: 5000 },
    { duration: '20s', target: 5000 },

    // Phase 5: 10000 VU
    { duration: '10s', target: 10000 },
    { duration: '30s', target: 10000 },

    // Cool-down
    { duration: '10s', target: 0 },
  ],
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
  thresholds: {
    'http_req_duration': ['p(99)<10000'],
  },
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const memberId = __VU * 100000 + __ITER;

  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      memberId: memberId,
      items: [{ productId: 'PROD01', quantity: 1 }],
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout: '10s',
    }
  );

  const ok = check(res, {
    'status 201': (r) => r.status === 201,
    'no timeout': (r) => r.timings.duration < 5000,
  });

  successRate.add(res.status === 201);
  orderDuration.add(res.timings.duration);

  if (res.status !== 201) errorCount.add(1);
  if (res.timings.duration >= 5000) timeoutCount.add(1);
}

export function handleSummary(data) {
  const med = data.metrics.order_duration_ms ? data.metrics.order_duration_ms.values.med : 0;
  const p90 = data.metrics.order_duration_ms ? data.metrics.order_duration_ms.values['p(90)'] : 0;
  const p99 = data.metrics.order_duration_ms ? data.metrics.order_duration_ms.values['p(99)'] : 0;
  const avg = data.metrics.order_duration_ms ? data.metrics.order_duration_ms.values.avg : 0;
  const totalReqs = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
  const totalDuration = 170; // approximate total test duration in seconds
  const avgTPS = totalReqs / totalDuration;

  const M = 40; // HikariCP pool size
  const W = avg / 1000; // avg processing time in seconds
  const lambdaMax = W > 0 ? M / W : 0;
  const lambdaSafe = lambdaMax * 0.7;
  const batchSize = Math.round(lambdaSafe / 10); // 100ms interval = 10 ticks/sec
  const intervalMs = 100;

  console.log('\n========================================');
  console.log('  DB 한계점 측정 결과 (Little\'s Law)');
  console.log('========================================');
  console.log(`  M (pool size)        : ${M}`);
  console.log(`  W (avg duration)     : ${avg.toFixed(2)}ms = ${W.toFixed(4)}s`);
  console.log(`  W (p90)              : ${p90.toFixed(2)}ms`);
  console.log(`  W (p99)              : ${p99.toFixed(2)}ms`);
  console.log(`  λ_max = M/W          : ${lambdaMax.toFixed(1)} TPS`);
  console.log(`  λ_safe (70%)         : ${lambdaSafe.toFixed(1)} TPS`);
  console.log(`  측정 avg TPS         : ${avgTPS.toFixed(1)}`);
  console.log('');
  console.log('  스케줄러 설정 도출:');
  console.log(`    interval-ms        : ${intervalMs}`);
  console.log(`    batch-size         : ${batchSize}`);
  console.log(`    throughput/sec     : ${(batchSize * (1000 / intervalMs)).toFixed(0)}`);
  console.log('');
  console.log(`  총 요청              : ${totalReqs}`);
  console.log(`  성공률               : ${data.metrics.success_rate ? (data.metrics.success_rate.values.rate * 100).toFixed(2) : 0}%`);
  console.log(`  에러 수              : ${data.metrics.error_count ? data.metrics.error_count.values.count : 0}`);
  console.log(`  타임아웃 수          : ${data.metrics.timeout_count ? data.metrics.timeout_count.values.count : 0}`);
  console.log('========================================\n');

  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}

import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';
