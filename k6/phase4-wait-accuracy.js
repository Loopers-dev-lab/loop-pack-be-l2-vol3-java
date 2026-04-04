import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const predictedWait = new Trend('predicted_wait_s', true);
const actualWait = new Trend('actual_wait_s', true);
const waitError = new Trend('wait_error_s', true);
const waitErrorPct = new Trend('wait_error_pct', true);
const samples = new Counter('samples');

export const options = {
  vus: 50,
  duration: '60s',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
  const memberId = __VU * 100000 + __ITER;

  const enterRes = http.post(
    `${BASE_URL}/api/v1/queue/enter`,
    JSON.stringify({ memberId: memberId }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  if (enterRes.status !== 200) return;

  const enterBody = JSON.parse(enterRes.body);
  const position = enterBody.data.position;
  const predicted = enterBody.data.estimatedWaitSeconds;
  const enterTime = Date.now();

  for (let i = 0; i < 60; i++) {
    sleep(1);
    const posRes = http.get(`${BASE_URL}/api/v1/queue/position?memberId=${memberId}`);
    if (posRes.status === 200) {
      const body = JSON.parse(posRes.body);
      if (body.data && body.data.token) {
        const actualSec = (Date.now() - enterTime) / 1000;
        predictedWait.add(predicted);
        actualWait.add(actualSec);
        waitError.add(Math.abs(actualSec - predicted));
        if (predicted > 0) {
          waitErrorPct.add(Math.abs(actualSec - predicted) / predicted * 100);
        }
        samples.add(1);
        break;
      }
    }
  }
}
