import http from 'k6/http';
import { fail } from 'k6';

const DEFAULT_BASE_URL = 'http://localhost:8080';
const DEFAULT_PASSWORD = 'Test1234!@';
const DEFAULT_ADMIN_LDAP = 'loopers.admin';
const DEFAULT_POINT_AMOUNT = Number(__ENV.K6_POINT_AMOUNT || 1000);
const DEFAULT_COUPON_POOL_SIZE = Number(__ENV.K6_COUPON_POOL_SIZE || 20);
const DEFAULT_FAILURE_LOG_LIMIT = Number(__ENV.K6_FAILURE_LOG_LIMIT || 20);

function jsonHeaders(extraHeaders = {}) {
  return {
    'Content-Type': 'application/json',
    ...extraHeaders,
  };
}

function currentVu() {
  return typeof __VU !== 'undefined' ? __VU : 0;
}

function currentIter() {
  return typeof __ITER !== 'undefined' ? __ITER : 0;
}

function mustHaveStatus(response, expectedStatuses, message, context = {}) {
  const matched = expectedStatuses.includes(response.status);
  if (matched) {
    return;
  }

  const failure = parseFailure(response);
  const payload = {
    type: 'K6_SETUP_FAILURE',
    stage: context.stage || 'unknown_setup_stage',
    runTag: context.runTag || 'unknown',
    status: failure.status,
    errorCode: failure.errorCode,
    message: failure.message,
    loginId: context.loginId || 'n/a',
  };
  console.error(`K6_SETUP_FAILURE ${JSON.stringify(payload)}`);

  fail(`${message} (status=${response.status}, body=${response.body})`);
}

function randomSuffix() {
  const now = (Date.now() % 100000000).toString(36);
  const rand = Math.floor(Math.random() * 1_000_000)
    .toString(36)
    .padStart(4, '0');
  return `${now}${rand}`;
}

function sanitizeAlphaNum(value) {
  return (value || '').replace(/[^a-zA-Z0-9]/g, '');
}

function buildRunTag() {
  const envTag = sanitizeAlphaNum(__ENV.K6_RUN_TAG || '');
  if (envTag.length > 0) {
    return envTag.slice(-8);
  }
  return sanitizeAlphaNum(Date.now().toString(36)).slice(-8);
}

function buildLoginId(runTag) {
  const suffix = randomSuffix().slice(0, 8);
  return `k6${runTag}${suffix}`.slice(0, 20);
}

function toLocalDateTimeString(date) {
  const iso = date.toISOString();
  return iso.slice(0, 19);
}

function pickSeedProduct(baseUrl, runTag) {
  const response = http.get(`${baseUrl}/api/v1/products?page=0&size=1`, {
    tags: { name: 'seed_product_lookup' },
  });
  mustHaveStatus(response, [200], 'failed to fetch seed product', {
    stage: 'seed_product_lookup',
    runTag,
  });

  const products = response.json('data.items');
  if (!Array.isArray(products) || products.length === 0) {
    fail('no product found in /api/v1/products. seed data is required for k6 fixture setup.');
  }

  return products[0];
}

function registerMember(baseUrl, loginId, password, runTag) {
  const response = http.post(
    `${baseUrl}/api/v1/members`,
    JSON.stringify({
      loginId,
      password,
      name: 'Ksixtester',
      birthDate: '19900101',
      email: `${loginId}@k6.test`,
      phone: '010-0000-0000',
    }),
    {
      headers: jsonHeaders({
        'X-K6-Run-Id': runTag,
        'X-K6-Scenario': 'setup_member_register',
        'X-Request-Id': `${runTag}-setup-member-${currentVu()}-${currentIter()}`,
      }),
      tags: { name: 'member_register' },
    }
  );

  mustHaveStatus(response, [201], 'failed to register k6 member', {
    stage: 'member_register',
    runTag,
    loginId,
  });
}

function createLoadProduct(baseUrl, seedProduct, runTag) {
  const response = http.post(
    `${baseUrl}/api/v1/products`,
    JSON.stringify({
      name: `k6-order-product-${runTag}-${randomSuffix()}`,
      price: 9900,
      stock: 500000,
      description: 'k6 load test product',
      categoryId: seedProduct.categoryId,
      brandId: seedProduct.brandId,
    }),
    {
      headers: jsonHeaders({
        'X-K6-Run-Id': runTag,
        'X-K6-Scenario': 'setup_product_create',
        'X-Request-Id': `${runTag}-setup-product-${currentVu()}-${currentIter()}`,
      }),
      tags: { name: 'load_product_create' },
    }
  );

  mustHaveStatus(response, [201], 'failed to create k6 load product', {
    stage: 'product_create',
    runTag,
  });
  const productId = response.json('data.id');
  if (!productId) {
    fail(`created product response has no data.id (body=${response.body})`);
  }
  return productId;
}

function createCoupon(baseUrl, runTag) {
  const expiredAt = toLocalDateTimeString(new Date(Date.now() + 3 * 24 * 60 * 60 * 1000));
  const response = http.post(
    `${baseUrl}/api-admin/v1/coupons`,
    JSON.stringify({
      name: `k6-coupon-${runTag}-${randomSuffix()}`,
      type: 'FIXED',
      value: 1000,
      minOrderAmount: 0,
      expiredAt,
    }),
    {
      headers: jsonHeaders({
        'X-Loopers-Ldap': DEFAULT_ADMIN_LDAP,
        'X-K6-Run-Id': runTag,
        'X-K6-Scenario': 'setup_coupon_create',
        'X-Request-Id': `${runTag}-setup-coupon-${currentVu()}-${currentIter()}`,
      }),
      tags: { name: 'coupon_create' },
    }
  );

  mustHaveStatus(response, [201], 'failed to create coupon', {
    stage: 'coupon_create',
    runTag,
  });
  const couponId = response.json('data.id');
  if (!couponId) {
    fail(`created coupon response has no data.id (body=${response.body})`);
  }
  return couponId;
}

function issueCouponToMember(baseUrl, couponId, loginId, password, runTag) {
  const response = http.post(`${baseUrl}/api/v1/coupons/${couponId}/issue`, null, {
    headers: jsonHeaders({
      'X-Loopers-LoginId': loginId,
      'X-Loopers-LoginPw': password,
      'X-K6-Run-Id': runTag,
      'X-K6-Scenario': 'setup_coupon_issue',
      'X-Request-Id': `${runTag}-setup-coupon-issue-${currentVu()}-${currentIter()}`,
    }),
    tags: { name: 'coupon_issue' },
  });
  mustHaveStatus(response, [201], 'failed to issue coupon to k6 member', {
    stage: 'coupon_issue',
    runTag,
    loginId,
  });
}

export function setupOrderFixture() {
  const baseUrl = __ENV.BASE_URL || DEFAULT_BASE_URL;
  const password = __ENV.K6_MEMBER_PASSWORD || DEFAULT_PASSWORD;
  const runTag = buildRunTag();
  const loginId = buildLoginId(runTag);

  const seedProduct = pickSeedProduct(baseUrl, runTag);
  registerMember(baseUrl, loginId, password, runTag);
  const productId = createLoadProduct(baseUrl, seedProduct, runTag);
  const couponIds = [];
  for (let i = 0; i < DEFAULT_COUPON_POOL_SIZE; i += 1) {
    const couponId = createCoupon(baseUrl, runTag);
    issueCouponToMember(baseUrl, couponId, loginId, password, runTag);
    couponIds.push(couponId);
  }

  return {
    baseUrl,
    runTag,
    loginId,
    password,
    productId,
    couponIds,
    pointAmount: DEFAULT_POINT_AMOUNT,
  };
}

export function pickCouponIdForVu(fixture) {
  const coupons = fixture.couponIds || [];
  if (!Array.isArray(coupons) || coupons.length === 0) {
    fail('coupon pool is empty. check setup fixture.');
  }
  const idx = (__VU - 1) % coupons.length;
  return coupons[idx];
}

export function authHeaders(fixture) {
  return jsonHeaders({
    'X-Loopers-LoginId': fixture.loginId,
    'X-Loopers-LoginPw': fixture.password,
  });
}

export function buildTraceHeaders(fixture, scenario) {
  return {
    'X-K6-Run-Id': fixture.runTag,
    'X-K6-Scenario': scenario,
    'X-K6-Vu': String(currentVu()),
    'X-K6-Iter': String(currentIter()),
    'X-Request-Id': `${fixture.runTag}-${scenario}-${currentVu()}-${currentIter()}`,
  };
}

export function parseFailure(response) {
  let errorCode = 'UNKNOWN';
  let message = '';

  try {
    const meta = response.json('meta');
    if (meta) {
      errorCode = meta.errorCode || errorCode;
      message = meta.message || message;
    }
  } catch (e) {
    errorCode = `PARSE_ERROR:${e && e.name ? e.name : 'unknown'}`;
  }

  if (!message && typeof response.body === 'string') {
    message = response.body.slice(0, 300);
  }

  return {
    status: response.status,
    errorCode,
    message,
  };
}

export function logFailure(fixture, scenario, stage, response, extra = {}) {
  const failure = parseFailure(response);
  const payload = {
    type: 'K6_FAILURE',
    runTag: fixture.runTag,
    scenario,
    stage,
    loginId: fixture.loginId,
    vu: currentVu(),
    iter: currentIter(),
    status: failure.status,
    errorCode: failure.errorCode,
    message: failure.message,
    ...extra,
  };
  console.error(`K6_FAILURE ${JSON.stringify(payload)}`);
}

export function shouldLogFailure(localFailureCount) {
  return localFailureCount < DEFAULT_FAILURE_LOG_LIMIT;
}

export function createOrder(fixture, quantity = 1, options = {}) {
  const useCoupon = options.useCoupon === true;
  const usePoint = options.usePoint !== false;
  const pointAmount = usePoint ? fixture.pointAmount : 0;
  const couponId = useCoupon ? (options.couponId || pickCouponIdForVu(fixture)) : null;
  const scenario = options.scenario || 'order_create';

  const response = http.post(
    `${fixture.baseUrl}/api/v1/orders`,
    JSON.stringify({
      items: [{ productId: fixture.productId, quantity }],
      couponId,
      pointAmount,
      cardType: 'SAMSUNG',
      cardNo: '1234-5678-1234-5678',
    }),
    {
      headers: {
        ...authHeaders(fixture),
        ...buildTraceHeaders(fixture, scenario),
      },
      tags: {
        name: 'order_create',
        scenario,
        use_coupon: String(useCoupon),
        point_amount: String(pointAmount),
      },
    }
  );

  return {
    response,
    orderId: response.status === 201 ? response.json('data.id') : null,
  };
}

export function cancelOrder(fixture, orderId, expectedStatuses = [200]) {
  const scenario = expectedStatuses.includes(409) ? 'order_cancel_idempotency' : 'order_cancel';
  return http.patch(`${fixture.baseUrl}/api/v1/orders/${orderId}/cancel`, null, {
    headers: {
      ...authHeaders(fixture),
      ...buildTraceHeaders(fixture, scenario),
    },
    tags: {
      name: 'order_cancel',
      scenario,
    },
    responseCallback: http.expectedStatuses(...expectedStatuses),
  });
}
