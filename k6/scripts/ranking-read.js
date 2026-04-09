import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const rankingReadSuccess = new Rate('ranking_read_success');
const rankingReadCount = new Counter('ranking_read_count');
const rankingReadFailureCount = new Counter('ranking_read_failure_count');
const rankingReadFailure4xx = new Counter('ranking_read_failure_4xx');
const rankingReadFailure5xx = new Counter('ranking_read_failure_5xx');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WINDOW = __ENV.WINDOW || 'DAILY';
const PAGE = Number(__ENV.PAGE || '1');
const PAGE_MIN = Number(__ENV.PAGE_MIN || String(PAGE));
const PAGE_MAX = Number(__ENV.PAGE_MAX || String(PAGE));
const SIZE = Number(__ENV.SIZE || '20');
const DATE = __ENV.DATE || '';
const HOUR = __ENV.HOUR || '';
const SCENARIO_NAME = __ENV.SCENARIO_NAME || 'ranking-read';

function resolvePage() {
  if (PAGE_MAX <= PAGE_MIN) {
    return PAGE_MIN;
  }
  return PAGE_MIN + Math.floor(Math.random() * (PAGE_MAX - PAGE_MIN + 1));
}

function buildQueryString(page) {
  const params = [
    `window=${encodeURIComponent(WINDOW)}`,
    `page=${page}`,
    `size=${SIZE}`,
  ];
  if (DATE.length > 0) {
    params.push(`date=${encodeURIComponent(DATE)}`);
  }
  if (HOUR.length > 0) {
    params.push(`hour=${encodeURIComponent(HOUR)}`);
  }
  return params.join('&');
}

function recordFailure(response) {
  rankingReadFailureCount.add(1);
  if (response.status >= 400 && response.status < 500) {
    rankingReadFailure4xx.add(1);
  }
  if (response.status >= 500) {
    rankingReadFailure5xx.add(1);
  }
}

export const options = {
  scenarios: {
    ranking_read: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || '50'),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    ranking_read_success: ['rate>0.99'],
  },
  tags: {
    scenario_name: SCENARIO_NAME,
  },
};

export default function () {
  const page = resolvePage();
  const response = http.get(`${BASE_URL}/api/v1/rankings?${buildQueryString(page)}`, {
    tags: { name: 'ranking_read', page: String(page) },
  });

  const success = check(response, {
    'ranking status is 200': (r) => r.status === 200,
    'ranking meta result is SUCCESS': (r) => r.json('meta.result') === 'SUCCESS',
    'ranking items is array': (r) => Array.isArray(r.json('data.items')),
  });

  rankingReadSuccess.add(success);
  if (success) {
    rankingReadCount.add(1);
  } else {
    recordFailure(response);
  }

  sleep(Math.random() * 0.2);
}
