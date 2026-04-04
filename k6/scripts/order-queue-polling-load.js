import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const LOGIN_ID = __ENV.LOGIN_ID || ''
const LOGIN_ID_PREFIX = __ENV.LOGIN_ID_PREFIX || 'orderqueue'
const LOGIN_PW = __ENV.LOGIN_PW || 'Test1234!@'
const SCENARIO_NAME = __ENV.SCENARIO_NAME || 'queue-polling-100'
const POLLING_SECONDS = Number(__ENV.POLLING_SECONDS || '1')

function resolveLoginId() {
  if (LOGIN_ID.length > 0) {
    return LOGIN_ID
  }
  return `${LOGIN_ID_PREFIX}${String(__VU).padStart(5, '0')}`
}

function buildHeaders() {
  return {
    'X-Loopers-LoginId': resolveLoginId(),
    'X-Loopers-LoginPw': LOGIN_PW,
  }
}

export const options = {
  scenarios: {
    queue_polling: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || '100'),
      duration: __ENV.DURATION || '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
  },
  tags: {
    scenario_name: SCENARIO_NAME,
  },
}

export default function () {
  const response = http.get(`${BASE_URL}/api/v1/order-queue/me/realtime`, { headers: buildHeaders() })

  check(response, {
    'status is 200': (r) => r.status === 200,
  })

  sleep(POLLING_SECONDS)
}
