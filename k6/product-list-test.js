import http from 'k6/http';
import { check } from 'k6';

// ─── Config ─────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_ID = __ENV.LOGIN_ID || 'loopers';
const LOGIN_PW = __ENV.LOGIN_PW || 'loopersloopers';

// ─── Load Stages ────────────────────────────────────────
// Warm-up(30s) → Ramp-up(1m) → Stress(30s) → Ramp-down(1m), 총 약 3분
const LOAD_STAGES = [
  { duration: '30s', target: 50 },
  { duration: '1m', target: 100 },
  { duration: '30s', target: 200 },
  { duration: '1m', target: 0 },
];

// ─── Fixed Parameters ───────────────────────────────────
const PAGE = 0;
const SIZE = 20;

// ─── Scenario Weights ───────────────────────────────────
// 정렬 옵션 × 인증 여부 × 브랜드 필터 조합
const SCENARIO_WEIGHTS = {
  listByLikesAsGuest:       0.20,   // 좋아요순 · 비회원
  listByLikesAsMember:      0.15,   // 좋아요순 · 회원
  listByLatestAsGuest:      0.15,   // 최신순 · 비회원
  listByLatestAsMember:     0.10,   // 최신순 · 회원
  listByPriceAsGuest:       0.10,   // 가격순 · 비회원
  listByPriceAsMember:      0.05,   // 가격순 · 회원
  listByBrandLikesAsGuest:  0.15,   // 브랜드+좋아요순 · 비회원
  listByBrandLikesAsMember: 0.10,   // 브랜드+좋아요순 · 회원
};

// ─── Options ────────────────────────────────────────────
const scenarios = {};
for (const [name, weight] of Object.entries(SCENARIO_WEIGHTS)) {
  scenarios[name] = {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: LOAD_STAGES.map((s) => ({
      duration: s.duration,
      target: Math.max(Math.ceil(s.target * weight), s.target > 0 ? 1 : 0),
    })),
    exec: name,
  };
}

export const options = {
  scenarios,
  thresholds: {
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],
    'http_req_failed': ['rate<0.01'],
  },
};

// ─── HTTP Headers ───────────────────────────────────────
const GUEST_HEADERS = {
  headers: { 'Content-Type': 'application/json' },
};

const MEMBER_HEADERS = {
  headers: {
    'Content-Type': 'application/json',
    'X-Loopers-LoginId': LOGIN_ID,
    'X-Loopers-LoginPw': LOGIN_PW,
  },
};

// ─── Random Generators ──────────────────────────────────

/** brandId 1~10 중 랜덤 */
function randomBrandId() {
  return Math.floor(Math.random() * 10) + 1;
}

// ─── Request Helpers ────────────────────────────────────

function fetchProductList(sort, httpParams, brandId) {
  let url = `${BASE_URL}/api/v1/products?sort=${sort}&page=${PAGE}&size=${SIZE}`;
  if (brandId) url += `&brandId=${brandId}`;
  const res = http.get(url, httpParams);
  check(res, { 'status is 200': (r) => r.status === 200 });
}

// ─── Scenario Functions ─────────────────────────────────

// 목록 조회 — 좋아요순
export function listByLikesAsGuest() { fetchProductList('LIKE_COUNT_DESC', GUEST_HEADERS); }
export function listByLikesAsMember() { fetchProductList('LIKE_COUNT_DESC', MEMBER_HEADERS); }

// 목록 조회 — 최신순
export function listByLatestAsGuest() { fetchProductList('CREATED_AT_DESC', GUEST_HEADERS); }
export function listByLatestAsMember() { fetchProductList('CREATED_AT_DESC', MEMBER_HEADERS); }

// 목록 조회 — 가격순
export function listByPriceAsGuest() { fetchProductList('PRICE_ASC', GUEST_HEADERS); }
export function listByPriceAsMember() { fetchProductList('PRICE_ASC', MEMBER_HEADERS); }

// 목록 조회 — 브랜드 + 좋아요순
export function listByBrandLikesAsGuest() { fetchProductList('LIKE_COUNT_DESC', GUEST_HEADERS, randomBrandId()); }
export function listByBrandLikesAsMember() { fetchProductList('LIKE_COUNT_DESC', MEMBER_HEADERS, randomBrandId()); }
