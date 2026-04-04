import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const pollLatency = new Trend('poll_latency_ms', true);
const orderLatency = new Trend('order_latency_ms', true);
const pollSuccess = new Rate('poll_success_rate');
const orderSuccess = new Rate('order_success_rate');
const orderCount = new Counter('order_count');
const pollCount = new Counter('poll_count');

export const options = {
  scenarios: {
    // 대기열 + 주문 유저 (heavy: DB 접근)
    queue_order: {
      executor: 'constant-vus',
      vus: 100,
      duration: '30s',
      exec: 'queueAndOrder',
    },
    // 순번 조회만 하는 유저 (light: Redis만)
    polling: {
      executor: 'constant-vus',
      vus: 200,
      duration: '30s',
      exec: 'pollOnly',
      startTime: '5s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = 'http://localhost:8080';

export function queueAndOrder() {
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
    }
  );

  orderLatency.add(Date.now() - start);
  orderSuccess.add(orderRes.status === 201);
  orderCount.add(1);
}

export function pollOnly() {
  const memberId = __VU;
  const start = Date.now();
  const res = http.get(`${BASE_URL}/api/v1/queue/position?memberId=${memberId}`);
  pollLatency.add(Date.now() - start);
  pollSuccess.add(res.status === 200 || res.status === 404);
  pollCount.add(1);
  sleep(2);
}
