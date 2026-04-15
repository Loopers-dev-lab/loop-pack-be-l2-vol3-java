import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const DATE = __ENV.DATE || '20260411';

const weeklyDur = new Trend('weekly_duration', true);
const monthlyDur = new Trend('monthly_duration', true);
const dailyDur = new Trend('daily_duration', true);
const errorRate = new Rate('error_rate');
const emptyRate = new Rate('empty_result_rate');

export const options = {
  scenarios: {
    weekly_200rps: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 100,
      maxVUs: 400,
      exec: 'weeklyLoad',
      tags: { scenario: 'weekly_200rps' },
    },
    monthly_200rps: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 100,
      maxVUs: 400,
      startTime: '65s',
      exec: 'monthlyLoad',
      tags: { scenario: 'monthly_200rps' },
    },
    daily_200rps_baseline: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 100,
      maxVUs: 400,
      startTime: '130s',
      exec: 'dailyLoad',
      tags: { scenario: 'daily_200rps' },
    },
    weekly_1200rps_stress: {
      executor: 'constant-arrival-rate',
      rate: 1200,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 400,
      maxVUs: 1000,
      startTime: '195s',
      exec: 'weeklyLoad',
      tags: { scenario: 'weekly_1200rps' },
    },
  },
  thresholds: {
    'weekly_duration{scenario:weekly_200rps}': ['p(95)<50'],
    'monthly_duration{scenario:monthly_200rps}': ['p(95)<50'],
    'weekly_duration{scenario:weekly_1200rps}': ['p(95)<100'],
    'error_rate': ['rate<0.001'],
    'empty_result_rate': ['rate<0.001'],
  },
};

function hit(url, trend) {
  const res = http.get(url);
  trend.add(res.timings.duration);
  const ok = check(res, {
    'status 200': r => r.status === 200,
  });
  errorRate.add(!ok);
  let empty = false;
  try {
    const body = JSON.parse(res.body);
    empty = !(body.data && body.data.items && body.data.items.length > 0);
  } catch (e) {
    empty = true;
  }
  emptyRate.add(empty);
}

export function weeklyLoad() {
  const page = Math.floor(Math.random() * 5);
  hit(`${BASE_URL}/api/v1/rankings?period=weekly&date=${DATE}&page=${page}&size=20`, weeklyDur);
}

export function monthlyLoad() {
  const page = Math.floor(Math.random() * 5);
  hit(`${BASE_URL}/api/v1/rankings?period=monthly&date=${DATE}&page=${page}&size=20`, monthlyDur);
}

export function dailyLoad() {
  const page = Math.floor(Math.random() * 5);
  hit(`${BASE_URL}/api/v1/rankings?period=daily&date=${DATE}&page=${page}&size=20`, dailyDur);
}
