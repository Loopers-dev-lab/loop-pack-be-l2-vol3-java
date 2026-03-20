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

const firstCancelSuccess = new Rate('first_cancel_success');
const secondCancelConflict = new Rate('second_cancel_conflict');
const createFailureCount = new Counter('order_create_failure_count');
const createFailure4xx = new Counter('order_create_failure_4xx');
const createFailure5xx = new Counter('order_create_failure_5xx');

let failureLogCount = 0;

export const options = {
  scenarios: {
    idempotency: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 6),
      iterations: Number(__ENV.ITERATIONS || 60),
      maxDuration: __ENV.MAX_DURATION || '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
    first_cancel_success: ['rate>0.99'],
    second_cancel_conflict: ['rate>0.99'],
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
    scenario: 'order_cancel_idempotency',
  });
  const createSuccess = check(created.response, {
    'order create status is 201': (r) => r.status === 201,
    'order id exists': () => Boolean(created.orderId),
  });

  if (!createSuccess) {
    const failure = parseFailure(created.response);
    createFailureCount.add(1);
    if (failure.status >= 400 && failure.status < 500) {
      createFailure4xx.add(1);
    }
    if (failure.status >= 500) {
      createFailure5xx.add(1);
    }
    if (shouldLogFailure(failureLogCount)) {
      logFailure(fixture, 'order_cancel_idempotency', 'create_order', created.response, {
        orderId: created.orderId,
        pointAmount: fixture.pointAmount,
        couponId,
      });
      failureLogCount += 1;
    }
    firstCancelSuccess.add(false);
    secondCancelConflict.add(false);
    sleep(0.1);
    return;
  }

  const firstCancel = cancelOrder(fixture, created.orderId, [200]);
  const firstSuccess = check(firstCancel, {
    'first cancel status is 200': (r) => r.status === 200,
  });
  firstCancelSuccess.add(firstSuccess);
  if (!firstSuccess && shouldLogFailure(failureLogCount)) {
    logFailure(fixture, 'order_cancel_idempotency', 'first_cancel', firstCancel, {
      orderId: created.orderId,
      pointAmount: fixture.pointAmount,
      couponId,
    });
    failureLogCount += 1;
  }

  const secondCancel = cancelOrder(fixture, created.orderId, [409]);
  const secondConflict = check(secondCancel, {
    'second cancel status is 409': (r) => r.status === 409,
  });
  secondCancelConflict.add(secondConflict);
  if (!secondConflict && shouldLogFailure(failureLogCount)) {
    logFailure(fixture, 'order_cancel_idempotency', 'second_cancel', secondCancel, {
      orderId: created.orderId,
      pointAmount: fixture.pointAmount,
      couponId,
    });
    failureLogCount += 1;
  }

  sleep(Math.random() * 0.2);
}
