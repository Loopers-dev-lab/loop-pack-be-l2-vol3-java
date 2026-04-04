import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

/**
 * 대기열 활성화 상태에서 동일 부하 — DB 보호 효과 측정
 *
 * stress-no-queue.js 와 동일한 VU 단계로 실행하여 비교
 * queue.enabled=true 상태에서 실행 필요
 */

const enterSuccessRate = new Rate('enter_success_rate');
const orderSuccessRate = new Rate('order_success_rate');
const queueWait = new Trend('queue_wait_ms', true);
const orderDuration = new Trend('order_duration_ms', true);
const tokenTimeout = new Counter('token_timeout');
const queueFullCount = new Counter('queue_full_429');
const enterDuration = new Trend('enter_duration_ms', true);

export const options = {
  stages: [
    { duration: '10s', target: 100 },
    { duration: '20s', target: 100 },

    { duration: '10s', target: 500 },
    { duration: '20s', target: 500 },

    { duration: '10s', target: 1000 },
    { duration: '20s', target: 1000 },

    { duration: '10s', target: 5000 },
    { duration: '20s', target: 5000 },

    { duration: '10s', target: 10000 },
    { duration: '30s', target: 10000 },

    { duration: '10s', target: 0 },
  ],
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const memberId = __VU * 100000 + __ITER;

  // Step 1: 대기열 진입
  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter`,
    JSON.stringify({ memberId: memberId }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '10s' }
  );

  enterDuration.add(enterRes.timings.duration);
  enterSuccessRate.add(enterRes.status === 200);

  if (enterRes.status === 429) {
    queueFullCount.add(1);
    return;
  }
  if (enterRes.status !== 200) return;

  const enterBody = JSON.parse(enterRes.body);
  if (enterBody.data && enterBody.data.token) {
    placeOrder(memberId, enterBody.data.token, Date.now());
    return;
  }

  const enterTime = Date.now();

  // Step 2: 토큰 발급 대기 (Polling)
  let token = null;
  for (let i = 0; i < 60; i++) {
    sleep(1);
    const posRes = http.get(
      `${BASE_URL}/api/v1/queue/position?memberId=${memberId}`,
      { timeout: '5s' }
    );

    if (posRes.status === 200) {
      const body = JSON.parse(posRes.body);
      if (body.data && body.data.token) {
        token = body.data.token;
        break;
      }
    }
  }

  if (!token) {
    tokenTimeout.add(1);
    return;
  }

  queueWait.add(Date.now() - enterTime);
  placeOrder(memberId, token, enterTime);
}

function placeOrder(memberId, token, enterTime) {
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      memberId: memberId,
      items: [{ productId: 'PROD01', quantity: 1 }],
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Entry-Token': token,
      },
      timeout: '10s',
    }
  );

  orderDuration.add(orderRes.timings.duration);
  orderSuccessRate.add(orderRes.status === 201);
}

export function handleSummary(data) {
  const get = (name, stat) => {
    const m = data.metrics[name];
    return m ? m.values[stat] || 0 : 0;
  };

  console.log('\n============================================');
  console.log('  대기열 활성화 부하 테스트 결과');
  console.log('============================================');
  console.log(`  Enter 성공률        : ${(get('enter_success_rate', 'rate') * 100).toFixed(2)}%`);
  console.log(`  Enter p99           : ${get('enter_duration_ms', 'p(99)').toFixed(2)}ms`);
  console.log(`  Queue Full (429)    : ${get('queue_full_429', 'count')}`);
  console.log(`  Token Timeout       : ${get('token_timeout', 'count')}`);
  console.log(`  Queue Wait avg      : ${get('queue_wait_ms', 'avg').toFixed(0)}ms`);
  console.log(`  Queue Wait p99      : ${get('queue_wait_ms', 'p(99)').toFixed(0)}ms`);
  console.log(`  Order 성공률        : ${(get('order_success_rate', 'rate') * 100).toFixed(2)}%`);
  console.log(`  Order Duration avg  : ${get('order_duration_ms', 'avg').toFixed(2)}ms`);
  console.log(`  Order Duration p99  : ${get('order_duration_ms', 'p(99)').toFixed(2)}ms`);
  console.log('============================================\n');

  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
