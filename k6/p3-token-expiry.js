import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const tokenIssued = new Counter('token_issued');
const tokenUsed = new Counter('token_used');
const tokenExpired = new Counter('token_expired');
const orderSuccess = new Rate('order_success_rate');

export const options = {
  scenarios: {
    users: {
      executor: 'shared-iterations',
      vus: 50,
      iterations: 200,
      maxDuration: '120s',
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
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (enterRes.status !== 200) return;

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
  tokenIssued.add(1);

  // 30% of users intentionally don't order (simulate abandonment)
  if (__VU % 10 < 3) {
    sleep(5); // TTL=3s → token expires during this sleep
    tokenExpired.add(1);
    return;
  }

  // 70% order immediately
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

  orderSuccess.add(orderRes.status === 201);
  if (orderRes.status === 201) {
    tokenUsed.add(1);
  }
}
