import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

const pollingBlocked = new Counter('polling_blocked_429');
const pollingSuccess = new Counter('polling_success');
const positionReset = new Counter('position_reset');
const abuserPollingLatency = new Trend('abuser_polling_latency_ms', true);
const normalPollingLatency = new Trend('normal_polling_latency_ms', true);

export const options = {
  scenarios: {
    normal_users: {
      executor: 'constant-vus',
      vus: 50,
      duration: '30s',
      exec: 'normalUser',
    },
    abusers: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
      exec: 'abuser',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const HEADERS = { 'Content-Type': 'application/json' };

function enterQueue(memberId) {
  return http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
    headers: { ...HEADERS, 'X-USER-ID': String(memberId) },
  });
}

function pollPosition(memberId) {
  return http.get(`${BASE_URL}/api/v1/queue/position?memberId=${memberId}`, {
    headers: { 'X-USER-ID': String(memberId) },
  });
}

export function normalUser() {
  const memberId = 100000 + __VU;

  if (__ITER === 0) {
    enterQueue(memberId);
    sleep(1);
  }

  const start = Date.now();
  const res = pollPosition(memberId);
  normalPollingLatency.add(Date.now() - start);

  if (res.status === 200) {
    pollingSuccess.add(1);
    const body = JSON.parse(res.body);
    if (body.data && body.data.position) {
      // Track if position changed unexpectedly (reset)
    }
  } else if (res.status === 429) {
    pollingBlocked.add(1);
  }

  sleep(2); // normal 2-second polling interval
}

export function abuser() {
  const memberId = 200000 + __VU;

  if (__ITER === 0) {
    enterQueue(memberId);
    sleep(0.5);
  }

  // Abuser: polls every 0.3 seconds (simulating macro/refresh spam)
  const start = Date.now();
  const res = pollPosition(memberId);
  abuserPollingLatency.add(Date.now() - start);

  if (res.status === 200) {
    pollingSuccess.add(1);
  } else if (res.status === 429) {
    pollingBlocked.add(1);
  }

  // Check if position was reset (for after-test)
  if (res.status === 200) {
    try {
      const body = JSON.parse(res.body);
      if (body.data && body.data.status === 'WAITING') {
        // position tracking could go here
      }
    } catch (e) {}
  }

  sleep(0.3); // abusive polling interval
}
