import http from 'k6/http';
import { sleep, check } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const connectTime = new Trend('pg_connect_time', true);
const waitingTime = new Trend('pg_waiting_time', true);
const totalDuration = new Trend('pg_total_duration', true);
const successRate = new Rate('pg_success_rate');
const requestCount = new Counter('pg_request_count');

export const options = {
  scenarios: {
    baseline: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
      tags: { scenario: 'baseline' },
    },
    ramp_up: {
      executor: 'ramping-vus',
      startTime: '35s',
      startVUs: 5,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 100 },
        { duration: '15s', target: 0 },
      ],
      tags: { scenario: 'ramp_up' },
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

let orderSeq = 0;

export default function () {
  orderSeq++;
  const orderId = `ORD-VU${__VU}-IT${__ITER}-${orderSeq}`;

  const payload = JSON.stringify({
    orderId: orderId,
    cardType: 'SAMSUNG',
    cardNo: '1234-5678-9012-3456',
    amount: 10000,
    callbackUrl: 'http://localhost:8080/api/v1/payments/callback',
  });

  const res = http.post('http://localhost:8082/api/v1/payments', payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-USER-ID': String(__VU),
    },
  });

  connectTime.add(res.timings.connecting);
  waitingTime.add(res.timings.waiting);
  totalDuration.add(res.timings.duration);
  requestCount.add(1);
  successRate.add(res.status === 200);

  check(res, {
    'status 200 or 400': (r) => r.status === 200 || r.status === 400,
  });

  sleep(0.05);
}
