import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const enterLatency = new Trend('enter_latency_ms', true);
const positionLatency = new Trend('position_latency_ms', true);
const orderLatency = new Trend('order_latency_ms', true);
const orderSuccessRate = new Rate('order_success_rate');
const enterSuccessRate = new Rate('enter_success_rate');
const tokenTimeout = new Counter('token_timeout');
const totalOrders = new Counter('total_orders');

export const options = {
  scenarios: {
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '10s', target: 50 },
        { duration: '20s', target: 100 },
        { duration: '20s', target: 200 },
        { duration: '20s', target: 100 },
        { duration: '10s', target: 50 },
        { duration: '10s', target: 10 },
      ],
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const memberId = __VU * 100000 + __ITER;

  // Step 1: 대기열 진입
  const enterStart = Date.now();
  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter`,
    null,
    {
      headers: {
        'Content-Type': 'application/json',
        'X-USER-ID': String(memberId),
      },
    }
  );
  enterLatency.add(Date.now() - enterStart);
  enterSuccessRate.add(enterRes.status === 200);

  if (enterRes.status !== 200) return;

  // Step 2: 토큰 대기 (Polling)
  let token = null;
  for (let i = 0; i < 60; i++) {
    sleep(2);
    const posStart = Date.now();
    const posRes = http.get(
      `${BASE_URL}/api/v1/queue/position?memberId=${memberId}`,
      {
        headers: { 'X-USER-ID': String(memberId) },
      }
    );
    positionLatency.add(Date.now() - posStart);

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

  // Step 3: 주문
  const orderStart = Date.now();
  const orderRes = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      memberId: memberId,
      items: [{ productId: 'testprod1', quantity: 1 }],
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Entry-Token': token,
        'X-USER-ID': String(memberId),
      },
    }
  );

  orderLatency.add(Date.now() - orderStart);
  totalOrders.add(1);
  orderSuccessRate.add(orderRes.status === 201);
}
