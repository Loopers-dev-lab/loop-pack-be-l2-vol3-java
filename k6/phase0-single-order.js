import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const orderDuration = new Trend('order_duration_ms', true);

export const options = {
  vus: 1,
  duration: '30s',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      memberId: 1,
      items: [{ productId: 'testprod1', quantity: 1 }],
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, { 'status 201': (r) => r.status === 201 });
  orderDuration.add(res.timings.duration);
  sleep(0.1);
}
