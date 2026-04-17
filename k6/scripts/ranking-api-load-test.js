import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    daily_rankings: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
      exec: 'dailyRankings',
    },
    weekly_rankings: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
      exec: 'weeklyRankings',
      startTime: '10s',
    },
    monthly_rankings: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
      ],
      exec: 'monthlyRankings',
      startTime: '20s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_DATE = __ENV.TARGET_DATE || '20260415';
const PAGE_SIZE = __ENV.PAGE_SIZE || '20';

function request(period) {
  const url = `${BASE_URL}/api/v1/rankings?period=${period}&date=${TARGET_DATE}&size=${PAGE_SIZE}&page=1`;
  const res = http.get(url);

  check(res, {
    [`${period} status is 200`]: (r) => r.status === 200,
    [`${period} has rankings array`]: (r) => {
      const body = r.json();
      return !!body && !!body.data && Array.isArray(body.data.rankings);
    },
  });

  sleep(1);
}

export function dailyRankings() {
  request('daily');
}

export function weeklyRankings() {
  request('weekly');
}

export function monthlyRankings() {
  request('monthly');
}
