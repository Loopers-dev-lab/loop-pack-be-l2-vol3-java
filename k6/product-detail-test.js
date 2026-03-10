import http from 'k6/http';
import { check } from 'k6';

// ─── Config ─────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'loopers';
const LOGIN_PW = __ENV.LOGIN_PW || 'loopersloopers';

// 테스트 대상 상품 ID 범위 (DB에 존재하는 범위에 맞게 조정)
const MIN_PRODUCT_ID = 1;
const MAX_PRODUCT_ID = __ENV.MAX_PRODUCT_ID ? parseInt(__ENV.MAX_PRODUCT_ID) : 1000;

// ─── Load Stages ────────────────────────────────────────
// Warm-up(30s) → Ramp-up(1m) → Stress(30s) → Ramp-down(1m), 총 약 3분
const LOAD_STAGES = [
  { duration: '30s', target: 50 },
  { duration: '1m', target: 100 },
  { duration: '30s', target: 200 },
  { duration: '1m', target: 0 },
];

// ─── Options ────────────────────────────────────────────
export const options = {
  scenarios: {
    productDetail: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: LOAD_STAGES,
      exec: 'productDetail',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],
    'http_req_failed': ['rate<0.01'],
  },
};

// ─── HTTP Headers ───────────────────────────────────────
const HEADERS = {
  headers: {
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': LOGIN_ID,
    'X-Loopers-LoginPw': LOGIN_PW,
  },
};

// ─── Random Generators ──────────────────────────────────

/** 인기 상품에 트래픽이 집중되는 롱테일 분포 시뮬레이션 */
function randomProductId() {
  const r = Math.random();
  // 80%의 요청이 상위 20% 상품에 집중
  const range = MAX_PRODUCT_ID - MIN_PRODUCT_ID + 1;
  if (r < 0.8) {
    return MIN_PRODUCT_ID + Math.floor(Math.random() * Math.ceil(range * 0.2));
  }
  return MIN_PRODUCT_ID + Math.floor(Math.random() * range);
}

// ─── Scenario Function ─────────────────────────────────

export function productDetail() {
  const productId = randomProductId();
  const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, HEADERS);
  check(res, { 'status is 200': (r) => r.status === 200 });
}
