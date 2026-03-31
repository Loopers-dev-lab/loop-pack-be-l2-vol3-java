import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

/**
 * 액티브 토큰 한계 테스트
 *
 * "토큰을 받은 N명이 동시에 주문을 때리면 DB가 버티는가?"
 * queue.enabled=false 상태에서 직접 주문 API를 호출하여
 * 동시 주문자 수(=액티브 토큰 수)별 성공률/응답시간을 측정한다.
 *
 * 단계별로 VU를 고정하고 20초간 유지하여 안정 상태를 측정한다.
 */

const successRate = new Rate('success_rate');
const orderDuration = new Trend('order_duration_ms', true);
const errorCount = new Counter('error_count');
const timeoutCount = new Counter('timeout_count');
const connRefused = new Counter('conn_refused');

export const options = {
  scenarios: {
    // 각 단계를 독립 시나리오로 실행하여 정확한 VU별 측정
    vu_100: {
      executor: 'constant-vus',
      vus: 100,
      duration: '20s',
      startTime: '0s',
      tags: { stage: '100' },
    },
    vu_500: {
      executor: 'constant-vus',
      vus: 500,
      duration: '20s',
      startTime: '25s',
      tags: { stage: '500' },
    },
    vu_1000: {
      executor: 'constant-vus',
      vus: 1000,
      duration: '20s',
      startTime: '50s',
      tags: { stage: '1000' },
    },
    vu_5000: {
      executor: 'constant-vus',
      vus: 5000,
      duration: '20s',
      startTime: '75s',
      tags: { stage: '5000' },
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
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

  successRate.add(res.status === 201);
  orderDuration.add(res.timings.duration);

  if (res.status !== 201) errorCount.add(1);
  if (res.timings.duration >= 5000) timeoutCount.add(1);
  if (res.status === 0) connRefused.add(1);
}

export function handleSummary(data) {
  // 시나리오별 결과 추출
  const scenarios = ['vu_100', 'vu_500', 'vu_1000', 'vu_5000'];
  const vuLabels = [100, 500, 1000, 5000];

  console.log('\n===========================================================');
  console.log('  액티브 토큰 한계 테스트 결과');
  console.log('  (N명이 동시에 토큰을 들고 주문할 때 DB 수용 능력)');
  console.log('===========================================================');
  console.log('');

  const M = 40; // HikariCP pool

  for (let i = 0; i < scenarios.length; i++) {
    const tag = vuLabels[i];
    const scenKey = scenarios[i];

    // 시나리오별 메트릭은 tag로 필터링 안되므로 전체 요약에서 추론
    console.log(`  --- ${tag} 동시 주문자 ---`);
    console.log(`    DB 풀 포화도: ${(tag / M * 100).toFixed(0)}% (${tag}명 / ${M} pool)`);
    if (tag <= M) {
      console.log(`    예측: 풀 여유 있음, 성공률 높음`);
    } else if (tag <= M * 5) {
      console.log(`    예측: 커넥션 대기 발생, 응답 지연`);
    } else {
      console.log(`    예측: 대기 폭주, timeout/거절 다수`);
    }
    console.log('');
  }

  // 전체 요약
  const get = (name, stat) => {
    const m = data.metrics[name];
    return m ? (m.values[stat] || 0) : 0;
  };

  const totalReqs = get('http_reqs', 'count');
  const avgDuration = get('order_duration_ms', 'avg');
  const p99Duration = get('order_duration_ms', 'p(99)');
  const successPct = get('success_rate', 'rate') * 100;
  const errors = get('error_count', 'count');
  const timeouts = get('timeout_count', 'count');

  const W = avgDuration / 1000;
  const lambdaMax = W > 0 ? M / W : 0;
  const lambdaSafe = lambdaMax * 0.7;

  console.log('  === 전체 요약 ===');
  console.log(`  총 요청: ${totalReqs}`);
  console.log(`  성공률: ${successPct.toFixed(2)}%`);
  console.log(`  에러: ${errors}, 타임아웃: ${timeouts}`);
  console.log(`  응답시간 avg: ${avgDuration.toFixed(1)}ms, p99: ${p99Duration.toFixed(1)}ms`);
  console.log('');
  console.log('  === Little\'s Law ===');
  console.log(`  M = ${M}, W = ${W.toFixed(4)}s`);
  console.log(`  λ_max = ${lambdaMax.toFixed(1)} TPS`);
  console.log(`  λ_safe = ${lambdaSafe.toFixed(1)} TPS`);
  console.log('');
  console.log('  === 액티브 토큰 한계 계산 ===');
  console.log(`  동시 주문 가능 (기술적 최대): M = ${M}명`);
  console.log(`  안전 동시 주문 (70%): ${Math.floor(M * 0.7)}명`);
  console.log(`  토큰 보유자 행동 모델:`);
  console.log(`    즉시 주문 (플래시세일): 토큰=동시주문 → 최대 ${M}명`);
  console.log(`    30초 내 주문 (일반): 토큰 × (W/30) = 동시주문`);
  console.log(`      → 토큰 ${Math.floor(M * 30 / (W > 0 ? W : 1))}명까지 안전`);
  console.log('===========================================================\n');

  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
