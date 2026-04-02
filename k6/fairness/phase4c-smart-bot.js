import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

const normalOrderSuccess = new Counter('normal_order_success');
const normalOrderFail = new Counter('normal_order_fail');
const smartBotSuccess = new Counter('smartbot_success');
const smartBotFail = new Counter('smartbot_fail');

const normalQueueWait = new Trend('normal_queue_wait_ms', true);
const smartBotQueueWait = new Trend('smartbot_queue_wait_ms', true);

const orderDuration = new Trend('order_duration_ms', true);
const tokenTimeoutCount = new Counter('token_timeout_count');
const enterFailCount = new Counter('enter_fail_count');

const STOCK = 100;
const NORMAL_VU = 1000;
const BOT_VU = 40;

export const options = {
  scenarios: {
    normal_users: {
      executor: 'per-vu-iterations',
      vus: NORMAL_VU,
      iterations: 1,
      maxDuration: '300s',
      exec: 'normalUser',
      startTime: '0s',
    },
    smart_bot: {
      executor: 'per-vu-iterations',
      vus: BOT_VU,
      iterations: 1,
      maxDuration: '300s',
      exec: 'smartBot',
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
    JSON.stringify({ productName: 'k6 상품 01', price: 10000.00, stockQuantity: STOCK }),
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

  if (placeOrder(memberId, result.token, data.productId)) {
    normalOrderSuccess.add(1);
  } else {
    normalOrderFail.add(1);
  }
}

// Smart bot: knows the 2-second rule, waits exactly 2.1s after token discovery then orders instantly
export function smartBot(data) {
  const memberId = 500000 + __VU;
  const result = enterAndWaitForToken(memberId, 0.5, smartBotQueueWait);
  if (!result) return;

  // Smart bot knows the minimum interval is 2 seconds from ISSUANCE
  // Token was issued before discovery, so wait 2.1s from discovery to be safe
  sleep(2.1);

  if (placeOrder(memberId, result.token, data.productId)) {
    smartBotSuccess.add(1);
  } else {
    smartBotFail.add(1);
  }
}

function enterAndWaitForToken(memberId, pollInterval, waitTrend) {
  const userIdHeader = { 'X-USER-ID': String(memberId) };

  const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: userIdHeader, timeout: '10s',
  });

  if (enterRes.status !== 200) { enterFailCount.add(1); return null; }

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
      headers: userIdHeader, timeout: '5s',
    });
    if (posRes.status === 200) {
      const body = JSON.parse(posRes.body);
      if (body.data && body.data.token) { token = body.data.token; break; }
    }
  }

  if (!token) { tokenTimeoutCount.add(1); return null; }
  waitTrend.add(Date.now() - enterTime);
  return { token, waitMs: Date.now() - enterTime };
}

function placeOrder(memberId, token, productId) {
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({ memberId, items: [{ productId, quantity: 1 }] }),
    { headers: { 'Content-Type': 'application/json', 'X-Entry-Token': token }, timeout: '10s' }
  );
  orderDuration.add(orderRes.timings.duration);
  return orderRes.status === 201;
}

export function handleSummary(data) {
  const get = (name, stat) => {
    const m = data.metrics[name]; return m ? m.values[stat] || 0 : 0;
  };

  const nS = get('normal_order_success', 'count');
  const nF = get('normal_order_fail', 'count');
  const bS = get('smartbot_success', 'count');
  const bF = get('smartbot_fail', 'count');
  const total = nS + bS;

  console.log('\n============================================================');
  console.log('  Phase 4c: Smart Bot (knows 2s rule) Fairness Test');
  console.log('============================================================');
  console.log(`  Limited Stock         : ${STOCK}`);
  console.log(`  Token Timeout         : ${get('token_timeout_count', 'count')}`);
  console.log(`  Enter Failed          : ${get('enter_fail_count', 'count')}`);
  console.log('------------------------------------------------------------');
  console.log('  Group                  | VU   | Success | Fail | Rate');
  console.log('  -----------------------|------|---------|------|--------');
  console.log(`  Normal (poll 2s)       | ${String(NORMAL_VU).padStart(4)} | ${String(nS).padStart(7)} | ${String(nF).padStart(4)} | ${(nS+nF)>0?(nS/(nS+nF)*100).toFixed(1):0}%`);
  console.log(`  Smart Bot (wait 2.1s)  | ${String(BOT_VU).padStart(4)} | ${String(bS).padStart(7)} | ${String(bF).padStart(4)} | ${(bS+bF)>0?(bS/(bS+bF)*100).toFixed(1):0}%`);
  console.log('  -----------------------|------|---------|------|--------');
  console.log(`  Total                  | ${String(NORMAL_VU+BOT_VU).padStart(4)} | ${String(total).padStart(7)} |      |`);
  console.log('------------------------------------------------------------');
  console.log(`  Normal share of orders : ${total>0?(nS/total*100).toFixed(1):0}% (fair: 80%)`);
  console.log(`  Bot share of orders    : ${total>0?(bS/total*100).toFixed(1):0}% (fair: 20%)`);
  console.log('------------------------------------------------------------');
  console.log(`  Normal Queue Wait avg  : ${get('normal_queue_wait_ms', 'avg').toFixed(0)}ms`);
  console.log(`  SmartBot Queue Wait avg: ${get('smartbot_queue_wait_ms', 'avg').toFixed(0)}ms`);
  console.log(`  Order Duration avg     : ${get('order_duration_ms', 'avg').toFixed(0)}ms`);
  console.log('============================================================\n');

  return { stdout: textSummary(data, { indent: '  ', enableColors: true }) };
}
