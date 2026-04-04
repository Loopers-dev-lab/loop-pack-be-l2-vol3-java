import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const orderSuccessRate = new Rate('order_success_rate');
const queueWait = new Trend('queue_wait_ms', true);
const orderDuration = new Trend('order_duration_ms', true);
const tokenTimeoutCount = new Counter('token_timeout_count');
const orderSuccessCount = new Counter('order_success_count');
const orderFailCount = new Counter('order_fail_count');
const enterFailCount = new Counter('enter_fail_count');

const TOTAL_VU = 200;
const STOCK = 100;

export const options = {
  scenarios: {
    normal_users: {
      executor: 'per-vu-iterations',
      vus: TOTAL_VU,
      iterations: 1,
      maxDuration: '180s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_ID = __ENV.PRODUCT_ID || 'k6prod01';

export function setup() {
  const resetRes = http.put(
    `${BASE_URL}/api/v1/products/${PRODUCT_ID}`,
    JSON.stringify({
      productName: 'k6 상품 01',
      price: 10000.00,
      stockQuantity: STOCK,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  console.log(`[setup] Stock reset: status=${resetRes.status}, stock=${STOCK}`);

  const verifyRes = http.get(`${BASE_URL}/api/v1/products/${PRODUCT_ID}`);
  if (verifyRes.status === 200) {
    const body = JSON.parse(verifyRes.body);
    console.log(`[setup] Product verified: stock=${body.data.stockQuantity}`);
  }

  return { productId: PRODUCT_ID };
}

export default function (data) {
  const memberId = 100000 + __VU;
  const userIdHeader = { 'X-USER-ID': String(memberId) };

  // Step 1: Enter queue
  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: userIdHeader,
    timeout: '10s',
  });

  if (enterRes.status !== 200) {
    enterFailCount.add(1);
    return;
  }

  const enterBody = JSON.parse(enterRes.body);
  const enterTime = Date.now();

  // BYPASS mode: token issued immediately
  if (enterBody.data && enterBody.data.token) {
    sleep(Math.random() * 2 + 1);
    placeOrder(memberId, enterBody.data.token, enterTime, data.productId);
    return;
  }

  // Step 2: Poll for token (normal user: every 2 seconds)
  let token = null;
  for (let i = 0; i < 90; i++) {
    sleep(2);
    const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
      headers: userIdHeader,
      timeout: '5s',
    });

    if (posRes.status === 200) {
      const body = JSON.parse(posRes.body);
      if (body.data && body.data.token) {
        token = body.data.token;
        break;
      }
    }
  }

  if (!token) {
    tokenTimeoutCount.add(1);
    return;
  }

  queueWait.add(Date.now() - enterTime);

  // Normal user: 1~3 seconds delay before ordering
  sleep(Math.random() * 2 + 1);

  placeOrder(memberId, token, enterTime, data.productId);
}

function placeOrder(memberId, token, enterTime, productId) {
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      memberId: memberId,
      items: [{ productId: productId, quantity: 1 }],
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

  if (orderRes.status === 201) {
    orderSuccessCount.add(1);
    orderSuccessRate.add(true);
  } else {
    orderFailCount.add(1);
    orderSuccessRate.add(false);
  }
}

export function handleSummary(data) {
  const get = (name, stat) => {
    const m = data.metrics[name];
    return m ? m.values[stat] || 0 : 0;
  };

  const success = get('order_success_count', 'count');
  const fail = get('order_fail_count', 'count');
  const total = success + fail;
  const timeouts = get('token_timeout_count', 'count');
  const enterFails = get('enter_fail_count', 'count');

  console.log('\n============================================');
  console.log('  Phase 0: Baseline Fairness Test');
  console.log('============================================');
  console.log(`  Total VU              : ${TOTAL_VU}`);
  console.log(`  Limited Stock         : ${STOCK}`);
  console.log(`  Enter Failed          : ${enterFails}`);
  console.log(`  Token Timeout         : ${timeouts}`);
  console.log(`  Order Attempted       : ${total}`);
  console.log(`  Order Success         : ${success}`);
  console.log(`  Order Failed (stock)  : ${fail}`);
  console.log(`  Success Rate          : ${total > 0 ? (success / total * 100).toFixed(1) : 0}%`);
  console.log('--------------------------------------------');
  console.log(`  Queue Wait avg        : ${get('queue_wait_ms', 'avg').toFixed(0)}ms`);
  console.log(`  Queue Wait p95        : ${get('queue_wait_ms', 'p(95)').toFixed(0)}ms`);
  console.log(`  Queue Wait p99        : ${get('queue_wait_ms', 'p(99)').toFixed(0)}ms`);
  console.log(`  Order Duration avg    : ${get('order_duration_ms', 'avg').toFixed(0)}ms`);
  console.log(`  Order Duration p99    : ${get('order_duration_ms', 'p(99)').toFixed(0)}ms`);
  console.log('============================================\n');

  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
