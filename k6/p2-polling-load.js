import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';

const pollCount = new Counter('poll_requests_total');
const pollLatency = new Trend('poll_latency_ms', true);
const pollErrors = new Rate('poll_error_rate');

export const options = {
  scenarios: {
    fixed_2s: {
      executor: 'constant-vus',
      vus: 500,
      duration: '30s',
      env: { POLL_STRATEGY: 'fixed' },
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
  for (let i = 1; i <= 500; i++) {
    http.post(
      `${BASE_URL}/api/v1/queue/enter`,
      JSON.stringify({ memberId: i }),
      { headers: { 'Content-Type': 'application/json' } }
    );
  }
}

export default function () {
  const memberId = __VU;

  const start = Date.now();
  const posRes = http.get(`${BASE_URL}/api/v1/queue/position?memberId=${memberId}`);
  pollLatency.add(Date.now() - start);
  pollCount.add(1);
  pollErrors.add(posRes.status !== 200);

  sleep(2);
}
