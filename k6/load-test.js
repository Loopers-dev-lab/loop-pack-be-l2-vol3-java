import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('error_rate');
const productListTrend = new Trend('product_list_duration', true);
const productCursorTrend = new Trend('product_cursor_duration', true);
const orderListTrend = new Trend('order_list_duration', true);

export const options = {
  scenarios: {
    load: {
      executor: 'ramping-arrival-rate',
      startRate: 10,
      timeUnit: '1s',
      preAllocatedVUs: 500,
      maxVUs: 1500,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 200 },
        { duration: '1m', target: 500 },
        { duration: '2m', target: 1000 },
        { duration: '1m', target: 1000 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<3000'],
    error_rate: ['rate<0.10'],
  },
};

const BASE_URL = 'http://host.docker.internal:8080';

const PRODUCT_SORTS = ['PRICE_ASC', 'PRICE_DESC', 'LIKE_COUNT_DESC'];
const PAGE_SIZES = [10, 20, 30];

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function productList() {
  const sort = randomItem(PRODUCT_SORTS);
  const size = randomItem(PAGE_SIZES);
  const page = Math.floor(Math.random() * 5);

  const res = http.get(`${BASE_URL}/api/v1/products?sort=${sort}&page=${page}&size=${size}`, {
    tags: { endpoint: 'product_list' },
  });

  productListTrend.add(res.timings.duration);
  errorRate.add(res.status >= 400);

  check(res, {
    'product list status 200': (r) => r.status === 200,
    'product list has data': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body !== null;
      } catch {
        return false;
      }
    },
  });
}

function productCursor() {
  const sort = randomItem(PRODUCT_SORTS);
  const size = randomItem(PAGE_SIZES);

  const res = http.get(`${BASE_URL}/api/v1/products/cursor?sort=${sort}&size=${size}`, {
    tags: { endpoint: 'product_cursor' },
  });

  productCursorTrend.add(res.timings.duration);
  errorRate.add(res.status >= 400);

  check(res, {
    'cursor list status 200': (r) => r.status === 200,
  });
}

function orderList() {
  const memberId = Math.floor(Math.random() * 10) + 1;

  const res = http.get(
    `${BASE_URL}/api/v1/orders?memberId=${memberId}&page=0&size=10`,
    { tags: { endpoint: 'order_list' } }
  );

  orderListTrend.add(res.timings.duration);
  errorRate.add(res.status >= 400 && res.status !== 404);

  check(res, {
    'order list status ok': (r) => r.status === 200 || r.status === 404,
  });
}

function myLikes() {
  const memberId = Math.floor(Math.random() * 10) + 1;

  const res = http.get(
    `${BASE_URL}/api/v1/users/me/likes?memberId=${memberId}&page=0&size=10`,
    { tags: { endpoint: 'my_likes' } }
  );

  errorRate.add(res.status >= 500);

  check(res, {
    'my likes status ok': (r) => r.status === 200 || r.status === 404,
  });
}

export default function () {
  const rand = Math.random();

  if (rand < 0.40) {
    productList();
  } else if (rand < 0.70) {
    productCursor();
  } else if (rand < 0.85) {
    orderList();
  } else {
    myLikes();
  }

  sleep(Math.random() * 0.5 + 0.1);
}
