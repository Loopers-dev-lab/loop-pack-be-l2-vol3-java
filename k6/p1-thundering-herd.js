import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const orderLatency = new Trend('order_latency_ms', true);
const successRate = new Rate('success_rate');
const concurrentOrders = new Counter('concurrent_orders');

export const options = {
  vus: 100,
  duration: '60s',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const memberId = __VU * 100000 + __ITER;

  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter`,
    JSON.stringify({ memberId: memberId }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (enterRes.status !== 200) return;

  const enterTime = Date.now();
  let token = null;
  for (let i = 0; i < 30; i++) {
    sleep(1);
    const posRes = http.get(`${BASE_URL}/api/v1/queue/position?memberId=${memberId}`);
    if (posRes.status === 200) {
      const body = JSON.parse(posRes.body);
      if (body.data && body.data.token) {
        token = body.data.token;
        break;
      }
    }
  }

  if (!token) return;

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
      },
    }
  );

  orderLatency.add(Date.now() - orderStart);
  successRate.add(orderRes.status === 201);
  concurrentOrders.add(1);
}
