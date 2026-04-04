import http from 'k6/http';
import { Rate, Trend, Counter } from 'k6/metrics';

const successRate = new Rate('success_rate');
const orderDuration = new Trend('order_duration_ms', true);
const errorCount = new Counter('error_count');

export const options = {
  vus: __ENV.VUS ? parseInt(__ENV.VUS) : 100,
  duration: '20s',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max', 'count'],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const memberId = __VU * 100000 + __ITER;

  const res = http.post(
    `${BASE_URL}/api/v1/orders`,
    JSON.stringify({
      memberId: memberId,
      items: [{ productId: 'PROD01', quantity: 1 }],
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      timeout: '10s',
    }
  );

  successRate.add(res.status === 201);
  orderDuration.add(res.timings.duration);
  if (res.status !== 201) errorCount.add(1);
}
