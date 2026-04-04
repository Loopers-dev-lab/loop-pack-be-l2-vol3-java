import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const successRate = new Rate('success_rate');
const orderDuration = new Trend('order_duration_ms', true);
const errorCount = new Counter('error_count');

export const options = {
  stages: [
    { duration: '10s', target: 50 },
    { duration: '20s', target: 100 },
    { duration: '20s', target: 150 },
    { duration: '20s', target: 200 },
    { duration: '20s', target: 250 },
    { duration: '10s', target: 0 },
  ],
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      memberId: __VU,
      items: [{ productId: 'testprod1', quantity: 1 }],
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  const ok = check(res, {
    'status 201': (r) => r.status === 201,
    'no timeout': (r) => r.timings.duration < 5000,
  });

  successRate.add(res.status === 201);
  orderDuration.add(res.timings.duration);
  if (!ok) errorCount.add(1);
}
