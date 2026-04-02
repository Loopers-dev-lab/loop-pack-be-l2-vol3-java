import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const pollLatency = new Trend('poll_latency_ms', true);
const pollSuccess = new Rate('poll_success_rate');
const pollErrors = new Counter('poll_errors');
const pollTotal = new Counter('poll_total');

export const options = {
  scenarios: {
    spike: {
      executor: 'constant-vus',
      vus: 10000,
      duration: '30s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function setup() {
  // 10,000명 동시 대기열 진입
  const batchSize = 500;
  for (let batch = 0; batch < 20; batch++) {
    let requests = [];
    for (let i = 0; i < batchSize; i++) {
      const memberId = batch * batchSize + i + 1;
      requests.push(['POST', `${BASE_URL}/api/v1/queue/enter`, null, {
        headers: {
          'Content-Type': 'application/json',
          'X-USER-ID': String(memberId),
        },
      }]);
    }
    http.batch(requests);
  }
  sleep(2);
  return {};
}

export default function () {
  const memberId = (__VU % 10000) + 1;

  const start = Date.now();
  const res = http.get(
    `${BASE_URL}/api/v1/queue/position?memberId=${memberId}`,
    {
      headers: { 'X-USER-ID': String(memberId) },
    }
  );
  pollLatency.add(Date.now() - start);
  pollTotal.add(1);

  if (res.status === 200 || res.status === 429) {
    pollSuccess.add(true);
  } else {
    pollSuccess.add(false);
    pollErrors.add(1);
  }

  sleep(0.5);
}
