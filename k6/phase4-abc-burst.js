import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const actualWait = new Trend('actual_wait_s', true);
const errorA = new Trend('error_a_s', true);
const errorB = new Trend('error_b_s', true);
const errorC = new Trend('error_c_s', true);
const errorPctA = new Trend('error_pct_a', true);
const errorPctB = new Trend('error_pct_b', true);
const errorPctC = new Trend('error_pct_c', true);
const positionAtEnter = new Trend('position_at_enter', true);
const samples = new Counter('samples');
const timeouts = new Counter('timeouts');

export const options = {
  scenarios: {
    burst: {
      executor: 'shared-iterations',
      vus: 200,
      iterations: 500,
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

  const enterBody = JSON.parse(enterRes.body);
  const data = enterBody.data;
  const position = data.position;
  const predA = data.estimateA || 0;
  const predB = data.estimateB || 0;
  const predC = data.estimateC || 0;
  const enterTime = Date.now();

  positionAtEnter.add(position);

  let token = null;
  for (let i = 0; i < 60; i++) {
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

  if (!token) {
    timeouts.add(1);
    return;
  }

  const actual = (Date.now() - enterTime) / 1000;
  actualWait.add(actual);
  samples.add(1);

  errorA.add(Math.abs(actual - predA));
  errorB.add(Math.abs(actual - predB));
  errorC.add(Math.abs(actual - predC));

  if (actual > 0) {
    errorPctA.add(Math.abs(actual - predA) / actual * 100);
    errorPctB.add(Math.abs(actual - predB) / actual * 100);
    errorPctC.add(Math.abs(actual - predC) / actual * 100);
  }
}
