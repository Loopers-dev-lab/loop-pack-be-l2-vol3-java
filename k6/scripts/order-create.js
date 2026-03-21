import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { createOrder, logFailure, parseFailure, setupOrderFixture, shouldLogFailure } from '../lib/order-fixture.js';

const orderCreateSuccess = new Rate('order_create_success');
const orderCreateCount = new Counter('order_create_count');
const orderCreateFailureCount = new Counter('order_create_failure_count');
const orderCreateFailure4xx = new Counter('order_create_failure_4xx');
const orderCreateFailure5xx = new Counter('order_create_failure_5xx');
const orderCreateFailureConflict = new Counter('order_create_failure_conflict');
const orderCreateFailureBadRequest = new Counter('order_create_failure_bad_request');

let failureLogCount = 0;

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1200'],
    order_create_success: ['rate>0.99'],
  },
};

export function setup() {
  return setupOrderFixture();
}

export default function (fixture) {
  const { response, orderId } = createOrder(fixture, 1, {
    useCoupon: false,
    usePoint: true,
    scenario: 'order_create_point',
  });

  const success = check(response, {
    'order create status is 201': (r) => r.status === 201,
    'order id exists': () => Boolean(orderId),
  });

  orderCreateSuccess.add(success);
  if (success) {
    orderCreateCount.add(1);
  } else {
    const failure = parseFailure(response);
    orderCreateFailureCount.add(1);
    if (failure.status >= 400 && failure.status < 500) {
      orderCreateFailure4xx.add(1);
    }
    if (failure.status >= 500) {
      orderCreateFailure5xx.add(1);
    }
    if (failure.status === 409) {
      orderCreateFailureConflict.add(1);
    }
    if (failure.status === 400) {
      orderCreateFailureBadRequest.add(1);
    }

    if (shouldLogFailure(failureLogCount)) {
      logFailure(fixture, 'order_create_point', 'create_order', response, {
        orderId,
        pointAmount: fixture.pointAmount,
        couponUsed: false,
      });
      failureLogCount += 1;
    }
  }

  sleep(Math.random() * 0.3);
}
