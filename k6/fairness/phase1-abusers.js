import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const normalOrderSuccess = new Counter('normal_order_success');
const normalOrderFail = new Counter('normal_order_fail');
const abuserFastPollSuccess = new Counter('abuser_fastpoll_success');
const abuserFastPollFail = new Counter('abuser_fastpoll_fail');
const abuserMultiAccSuccess = new Counter('abuser_multiacc_success');
const abuserMultiAccFail = new Counter('abuser_multiacc_fail');

const normalQueueWait = new Trend('normal_queue_wait_ms', true);
const abuserFastPollQueueWait = new Trend('abuser_fastpoll_queue_wait_ms', true);
const abuserMultiAccQueueWait = new Trend('abuser_multiacc_queue_wait_ms', true);

const orderDuration = new Trend('order_duration_ms', true);
const tokenTimeoutCount = new Counter('token_timeout_count');
const enterFailCount = new Counter('enter_fail_count');

const STOCK = 100;

export const options = {
  scenarios: {
    normal_users: {
      executor: 'per-vu-iterations',
      vus: 160,
      iterations: 1,
      maxDuration: '180s',
      exec: 'normalUser',
      startTime: '0s',
    },
    abuser_fast_poll: {
      executor: 'per-vu-iterations',
      vus: 20,
      iterations: 1,
      maxDuration: '180s',
      exec: 'abuserFastPoll',
      startTime: '0s',
    },
    abuser_multi_account: {
      executor: 'per-vu-iterations',
      vus: 20,
      iterations: 1,
      maxDuration: '180s',
      exec: 'abuserMultiAccount',
      startTime: '0s',
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
  return { productId: PRODUCT_ID };
}

// Normal user: polls every 2s, 1~3s delay before order
export function normalUser(data) {
  const memberId = 200000 + __VU;
  const result = enterAndWaitForToken(memberId, 2, normalQueueWait);
  if (!result) return;

  sleep(Math.random() * 2 + 1);

  const success = placeOrder(memberId, result.token, data.productId);
  if (success) {
    normalOrderSuccess.add(1);
  } else {
    normalOrderFail.add(1);
  }
}

// Abuser A: polls every 0.1s (10x faster), orders instantly
export function abuserFastPoll(data) {
  const memberId = 300000 + __VU;
  const result = enterAndWaitForToken(memberId, 0.1, abuserFastPollQueueWait);
  if (!result) return;

  // Bot orders instantly (no human delay)
  const success = placeOrder(memberId, result.token, data.productId);
  if (success) {
    abuserFastPollSuccess.add(1);
  } else {
    abuserFastPollFail.add(1);
  }
}

// Abuser B: each VU uses 1 account but represents multi-account behavior
// (4 real people × 5 accounts each = 20 VU, same IPs in real life)
export function abuserMultiAccount(data) {
  const memberId = 400000 + __VU;
  const result = enterAndWaitForToken(memberId, 0.5, abuserMultiAccQueueWait);
  if (!result) return;

  sleep(Math.random() * 0.5);

  const success = placeOrder(memberId, result.token, data.productId);
  if (success) {
    abuserMultiAccSuccess.add(1);
  } else {
    abuserMultiAccFail.add(1);
  }
}

function enterAndWaitForToken(memberId, pollInterval, waitTrend) {
  const userIdHeader = { 'X-USER-ID': String(memberId) };

  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: userIdHeader,
    timeout: '10s',
  });

  if (enterRes.status !== 200) {
    enterFailCount.add(1);
    return null;
  }

  const enterBody = JSON.parse(enterRes.body);
  const enterTime = Date.now();

  if (enterBody.data && enterBody.data.token) {
    return { token: enterBody.data.token, waitMs: 0 };
  }

  let token = null;
  const maxPolls = Math.ceil(180 / pollInterval);
  for (let i = 0; i < maxPolls; i++) {
    sleep(pollInterval);
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
    return null;
  }

  waitTrend.add(Date.now() - enterTime);
  return { token, waitMs: Date.now() - enterTime };
}

function placeOrder(memberId, token, productId) {
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
  return orderRes.status === 201;
}

export function handleSummary(data) {
  const get = (name, stat) => {
    const m = data.metrics[name];
    return m ? m.values[stat] || 0 : 0;
  };

  const normalSuccess = get('normal_order_success', 'count');
  const normalFail = get('normal_order_fail', 'count');
  const fastPollSuccess = get('abuser_fastpoll_success', 'count');
  const fastPollFail = get('abuser_fastpoll_fail', 'count');
  const multiAccSuccess = get('abuser_multiacc_success', 'count');
  const multiAccFail = get('abuser_multiacc_fail', 'count');

  const totalSuccess = normalSuccess + fastPollSuccess + multiAccSuccess;
  const normalTotal = normalSuccess + normalFail;
  const fastPollTotal = fastPollSuccess + fastPollFail;
  const multiAccTotal = multiAccSuccess + multiAccFail;

  console.log('\n============================================================');
  console.log('  Phase 1: Abuser Mix Fairness Test');
  console.log('============================================================');
  console.log(`  Limited Stock         : ${STOCK}`);
  console.log(`  Token Timeout         : ${get('token_timeout_count', 'count')}`);
  console.log(`  Enter Failed          : ${get('enter_fail_count', 'count')}`);
  console.log('------------------------------------------------------------');
  console.log('  Group                  | VU  | Success | Fail | Rate');
  console.log('  -----------------------|-----|---------|------|--------');
  console.log(`  Normal (poll 2s)       | 160 | ${String(normalSuccess).padStart(7)} | ${String(normalFail).padStart(4)} | ${normalTotal > 0 ? (normalSuccess / normalTotal * 100).toFixed(1) : 0}%`);
  console.log(`  Abuser A (poll 0.1s)   |  20 | ${String(fastPollSuccess).padStart(7)} | ${String(fastPollFail).padStart(4)} | ${fastPollTotal > 0 ? (fastPollSuccess / fastPollTotal * 100).toFixed(1) : 0}%`);
  console.log(`  Abuser B (multi-acc)   |  20 | ${String(multiAccSuccess).padStart(7)} | ${String(multiAccFail).padStart(4)} | ${multiAccTotal > 0 ? (multiAccSuccess / multiAccTotal * 100).toFixed(1) : 0}%`);
  console.log('  -----------------------|-----|---------|------|--------');
  console.log(`  Total                  | 200 | ${String(totalSuccess).padStart(7)} |      |`);
  console.log('------------------------------------------------------------');
  console.log(`  Normal share of orders : ${totalSuccess > 0 ? (normalSuccess / totalSuccess * 100).toFixed(1) : 0}% (fair: 80%)`);
  console.log(`  Abuser share of orders : ${totalSuccess > 0 ? ((fastPollSuccess + multiAccSuccess) / totalSuccess * 100).toFixed(1) : 0}% (fair: 20%)`);
  console.log('------------------------------------------------------------');
  console.log(`  Normal Queue Wait avg  : ${get('normal_queue_wait_ms', 'avg').toFixed(0)}ms`);
  console.log(`  FastPoll Queue Wait avg: ${get('abuser_fastpoll_queue_wait_ms', 'avg').toFixed(0)}ms`);
  console.log(`  MultiAcc Queue Wait avg: ${get('abuser_multiacc_queue_wait_ms', 'avg').toFixed(0)}ms`);
  console.log(`  Order Duration avg     : ${get('order_duration_ms', 'avg').toFixed(0)}ms`);
  console.log('============================================================\n');

  return {
    stdout: textSummary(data, { indent: '  ', enableColors: true }),
  };
}
