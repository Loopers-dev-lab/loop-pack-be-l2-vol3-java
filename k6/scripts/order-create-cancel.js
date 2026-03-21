import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import {
  cancelOrder,
  createOrder,
  logFailure,
  parseFailure,
  pickCouponIdForVu,
  setupOrderFixture,
  shouldLogFailure,
} from '../lib/order-fixture.js';

const createSuccessRate = new Rate('order_create_success');
const cancelSuccessRate = new Rate('order_cancel_success');
const cancelCount = new Counter('order_cancel_count');
const createFailureCount = new Counter('order_create_failure_count');
const createFailure4xx = new Counter('order_create_failure_4xx');
const createFailure5xx = new Counter('order_create_failure_5xx');
const createFailureConflict = new Counter('order_create_failure_conflict');
const cancelFailureCount = new Counter('order_cancel_failure_count');
const cancelFailure4xx = new Counter('order_cancel_failure_4xx');
const cancelFailure5xx = new Counter('order_cancel_failure_5xx');

let failureLogCount = 0;

export const options = {
  vus: Number(__ENV.VUS || 12),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1400'],
    order_create_success: ['rate>0.99'],
    order_cancel_success: ['rate>0.99'],
  },
};

export function setup() {
  return setupOrderFixture();
}

export default function (fixture) {
  const couponId = pickCouponIdForVu(fixture);
  const created = createOrder(fixture, 1, {
    useCoupon: true,
    usePoint: true,
    couponId,
    scenario: 'order_create_cancel_flow',
  });
  const createSuccess = check(created.response, {
    'order create status is 201': (r) => r.status === 201,
    'order id exists': () => Boolean(created.orderId),
  });
  createSuccessRate.add(createSuccess);

  if (!createSuccess) {
    const failure = parseFailure(created.response);
    createFailureCount.add(1);
    if (failure.status >= 400 && failure.status < 500) {
      createFailure4xx.add(1);
    }
    if (failure.status >= 500) {
      createFailure5xx.add(1);
    }
    if (failure.status === 409) {
      createFailureConflict.add(1);
    }
    if (shouldLogFailure(failureLogCount)) {
      logFailure(fixture, 'order_create_cancel_flow', 'create_order', created.response, {
        orderId: created.orderId,
        pointAmount: fixture.pointAmount,
        couponId,
      });
      failureLogCount += 1;
    }
    sleep(0.1);
    return;
  }

  const cancelled = cancelOrder(fixture, created.orderId);
  const cancelSuccess = check(cancelled, {
    'order cancel status is 200': (r) => r.status === 200,
    'order status is CANCELLED': (r) => r.json('data.status') === 'CANCELLED',
  });
  cancelSuccessRate.add(cancelSuccess);

  if (cancelSuccess) {
    cancelCount.add(1);
  } else {
    const failure = parseFailure(cancelled);
    cancelFailureCount.add(1);
    if (failure.status >= 400 && failure.status < 500) {
      cancelFailure4xx.add(1);
    }
    if (failure.status >= 500) {
      cancelFailure5xx.add(1);
    }
    if (shouldLogFailure(failureLogCount)) {
      logFailure(fixture, 'order_create_cancel_flow', 'cancel_order', cancelled, {
        orderId: created.orderId,
        pointAmount: fixture.pointAmount,
        couponId,
      });
      failureLogCount += 1;
    }
  }

  sleep(Math.random() * 0.3);
}
