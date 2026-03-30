import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const orderAttempts = new Counter('order_attempts');
const orderSuccess = new Counter('order_success');
const orderFail = new Counter('order_fail');
const orderSuccessRate = new Rate('order_success_rate');
const orderLatency = new Trend('order_latency_ms', true);
const enterSuccess = new Counter('enter_success');
const enterFail = new Counter('enter_fail');
const fallbackAttempts = new Counter('fallback_attempts');
const fallbackSuccess = new Counter('fallback_success');

export const options = {
  scenarios: {
    full_flow: {
      executor: 'constant-vus',
      vus: 30,
      duration: '45s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const memberId = __VU * 1000000 + __ITER;

  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter`,
    JSON.stringify({ memberId: memberId }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '3s' }
  );

  if (enterRes.status === 200) {
    enterSuccess.add(1);

    let token = null;
    for (let i = 0; i < 10; i++) {
      sleep(1);
      const posRes = http.get(
        `${BASE_URL}/api/v1/queue/position?memberId=${memberId}`,
        { timeout: '3s' }
      );
      if (posRes.status === 200) {
        try {
          const body = JSON.parse(posRes.body);
          if (body.data && body.data.token) {
            token = body.data.token;
            break;
          }
        } catch (e) {}
      }
    }

    if (!token) return;

    orderAttempts.add(1);
    const start = Date.now();
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
        },
        timeout: '10s',
      }
    );
    orderLatency.add(Date.now() - start);
    orderSuccess.add(orderRes.status === 201 ? 1 : 0);
    orderFail.add(orderRes.status === 201 ? 0 : 1);
    orderSuccessRate.add(orderRes.status === 201);
  } else {
    enterFail.add(1);
    fallbackAttempts.add(1);

    orderAttempts.add(1);
    const start = Date.now();
    const orderRes = http.post(
      `${BASE_URL}/api/v1/orders`,
      JSON.stringify({
        memberId: memberId,
        items: [{ productId: 'testprod1', quantity: 1 }],
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Entry-Token': 'fallback-token',
        },
        timeout: '10s',
      }
    );
    orderLatency.add(Date.now() - start);
    if (orderRes.status === 201) {
      orderSuccess.add(1);
      fallbackSuccess.add(1);
      orderSuccessRate.add(true);
    } else {
      orderFail.add(1);
      orderSuccessRate.add(false);
    }
    sleep(0.2);
  }
}
