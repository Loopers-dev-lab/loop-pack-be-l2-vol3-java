import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const orderSuccessRate = new Rate('order_success_rate');
const queueWait = new Trend('queue_wait_ms', true);
const orderDuration = new Trend('order_duration_ms', true);
const tokenTimeout = new Counter('token_timeout');

export const options = {
  vus: 100,
  duration: '60s',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const memberId = __VU * 10000 + __ITER;

  // Step 1: 대기열 진입
  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter`,
    JSON.stringify({ memberId: memberId }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (enterRes.status !== 200) return;

  const enterTime = Date.now();

  // Step 2: 토큰 발급 대기 (Polling)
  let token = null;
  for (let i = 0; i < 30; i++) {
    sleep(1);
    const posRes = http.get(
      `${BASE_URL}/api/v1/queue/position?memberId=${memberId}`
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
      },
    }
  );

  orderDuration.add(Date.now() - orderStart);
  orderSuccessRate.add(orderRes.status === 201);
}
