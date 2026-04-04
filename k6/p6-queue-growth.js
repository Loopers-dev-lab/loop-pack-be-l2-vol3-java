import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const enterCount = new Counter('enter_requests');
const enterLatency = new Trend('enter_latency_ms', true);
const queueSize = new Trend('queue_size', true);

export const options = {
  scenarios: {
    incoming: {
      executor: 'constant-arrival-rate',
      rate: 300,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 500,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const memberId = Math.floor(Math.random() * 10000000);
  const start = Date.now();

  const res = http.post(
    `${BASE_URL}/api/v1/queue/enter`,
    JSON.stringify({ memberId: memberId }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  enterLatency.add(Date.now() - start);
  enterCount.add(1);

  if (res.status === 200) {
    const body = JSON.parse(res.body);
    queueSize.add(body.data.totalInQueue);
  }
}
