import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import {
  authHeaders,
  buildTraceHeaders,
  createOrder,
  logFailure,
  parseFailure,
  pickCouponIdForVu,
  setupOrderFixture,
  shouldLogFailure,
} from '../lib/order-fixture.js';

const raceStatusContract = new Rate('race_status_contract');
const raceNoServerError = new Rate('race_no_server_error');
const raceOrderQuerySuccess = new Rate('race_order_query_success');
const racePaymentQuerySuccess = new Rate('race_payment_query_success');
const raceCancelApplied = new Rate('race_cancel_applied');

const raceFailureCount = new Counter('race_failure_count');
const raceFailure4xx = new Counter('race_failure_4xx');
const raceFailure5xx = new Counter('race_failure_5xx');
const raceCancelConflictCount = new Counter('race_cancel_conflict_count');
const racePaymentCreatedCount = new Counter('race_payment_created_count');
const racePaymentConflictCount = new Counter('race_payment_conflict_count');
const raceUnresolvedAfterReconcileCount = new Counter('race_unresolved_after_reconcile_count');

let failureLogCount = 0;

export const options = {
  scenarios: {
    payment_cancel_race: {
      executor: 'shared-iterations',
      vus: Number(__ENV.VUS || 8),
      iterations: Number(__ENV.ITERATIONS || 80),
      maxDuration: __ENV.MAX_DURATION || '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<1800'],
    race_status_contract: ['rate>0.99'],
    race_no_server_error: ['rate>0.99'],
    race_order_query_success: ['rate>0.99'],
    race_payment_query_success: ['rate>0.99'],
  },
};

function countFailure(response) {
  const failure = parseFailure(response);
  raceFailureCount.add(1);
  if (failure.status >= 400 && failure.status < 500) {
    raceFailure4xx.add(1);
  }
  if (failure.status >= 500) {
    raceFailure5xx.add(1);
  }
}

export function setup() {
  return setupOrderFixture();
}

export default function (fixture) {
  const couponId = pickCouponIdForVu(fixture);
  const created = createOrder(fixture, 1, {
    useCoupon: true,
    usePoint: true,
    couponId,
    scenario: 'payment_cancel_race_setup',
  });

  const createSuccess = check(created.response, {
    'order create status is 201': (r) => r.status === 201,
    'order id exists': () => Boolean(created.orderId),
  });

  if (!createSuccess) {
    countFailure(created.response);
    if (shouldLogFailure(failureLogCount)) {
      logFailure(fixture, 'payment_cancel_race', 'create_order', created.response, {
        orderId: created.orderId,
        couponId,
      });
      failureLogCount += 1;
    }
    raceStatusContract.add(false);
    raceNoServerError.add(false);
    raceOrderQuerySuccess.add(false);
    racePaymentQuerySuccess.add(false);
    raceCancelApplied.add(false);
    sleep(0.1);
    return;
  }

  const cancelRequest = {
    method: 'PATCH',
    url: `${fixture.baseUrl}/api/v1/orders/${created.orderId}/cancel`,
    body: null,
    params: {
      headers: {
        ...authHeaders(fixture),
        ...buildTraceHeaders(fixture, 'payment_cancel_race_order_cancel'),
      },
      tags: {
        name: 'order_cancel',
        scenario: 'payment_cancel_race',
      },
      responseCallback: http.expectedStatuses(200, 409),
    },
  };

  const paymentRequest = {
    method: 'POST',
    url: `${fixture.baseUrl}/api/v1/payments`,
    body: JSON.stringify({
      orderId: created.orderId,
      cardType: 'SAMSUNG',
      cardNo: '1234-5678-1234-5678',
    }),
    params: {
      headers: {
        ...authHeaders(fixture),
        ...buildTraceHeaders(fixture, 'payment_cancel_race_payment_start'),
      },
      tags: {
        name: 'payment_start',
        scenario: 'payment_cancel_race',
      },
      responseCallback: http.expectedStatuses(201, 409, 400),
    },
  };

  const [cancelResponse, paymentResponse] = http.batch([cancelRequest, paymentRequest]);

  const contractSatisfied =
    (cancelResponse.status === 200 || cancelResponse.status === 409)
    && (paymentResponse.status === 201 || paymentResponse.status === 409 || paymentResponse.status === 400);
  raceStatusContract.add(contractSatisfied);

  if (cancelResponse.status === 409) {
    raceCancelConflictCount.add(1);
  }
  if (paymentResponse.status === 201) {
    racePaymentCreatedCount.add(1);
  }
  if (paymentResponse.status === 409) {
    racePaymentConflictCount.add(1);
  }

  const hasServerError = cancelResponse.status >= 500 || paymentResponse.status >= 500;
  raceNoServerError.add(!hasServerError);

  if (!contractSatisfied || hasServerError) {
    const cancelUnexpected = cancelResponse.status !== 200 && cancelResponse.status !== 409;
    const paymentUnexpected =
      paymentResponse.status !== 201
      && paymentResponse.status !== 409
      && paymentResponse.status !== 400;
    if (cancelUnexpected) {
      countFailure(cancelResponse);
    }
    if (paymentUnexpected) {
      countFailure(paymentResponse);
    }
    if (shouldLogFailure(failureLogCount)) {
      logFailure(fixture, 'payment_cancel_race', 'race_pair', cancelResponse, {
        orderId: created.orderId,
        couponId,
        paymentStatus: paymentResponse.status,
      });
      failureLogCount += 1;
    }
    if (paymentUnexpected && shouldLogFailure(failureLogCount)) {
      logFailure(fixture, 'payment_cancel_race', 'race_pair_payment', paymentResponse, {
        orderId: created.orderId,
        couponId,
        cancelStatus: cancelResponse.status,
      });
      failureLogCount += 1;
    }
  }

  const orderResponse = http.get(`${fixture.baseUrl}/api/v1/orders/${created.orderId}`, {
    headers: {
      ...authHeaders(fixture),
      ...buildTraceHeaders(fixture, 'payment_cancel_race_order_get'),
    },
    tags: {
      name: 'order_get',
      scenario: 'payment_cancel_race',
    },
    responseCallback: http.expectedStatuses(200),
  });

  const orderQueryOk = orderResponse.status === 200;
  raceOrderQuerySuccess.add(orderQueryOk);

  let orderCancelled = false;
  if (orderQueryOk) {
    orderCancelled = orderResponse.json('data.status') === 'CANCELLED';
  }
  raceCancelApplied.add(cancelResponse.status === 200 ? orderCancelled : true);

  const reconcileResponse = http.post(`${fixture.baseUrl}/api/v1/payments/${created.orderId}/reconcile`, null, {
    headers: {
      ...authHeaders(fixture),
      ...buildTraceHeaders(fixture, 'payment_cancel_race_payment_reconcile'),
    },
    tags: {
      name: 'payment_reconcile',
      scenario: 'payment_cancel_race',
    },
    responseCallback: http.expectedStatuses(200, 404, 409),
  });

  if (reconcileResponse.status >= 500) {
    countFailure(reconcileResponse);
    if (shouldLogFailure(failureLogCount)) {
      logFailure(fixture, 'payment_cancel_race', 'reconcile', reconcileResponse, {
        orderId: created.orderId,
        couponId,
      });
      failureLogCount += 1;
    }
  }

  const paymentQueryResponse = http.get(`${fixture.baseUrl}/api/v1/payments?orderId=${created.orderId}`, {
    headers: {
      ...authHeaders(fixture),
      ...buildTraceHeaders(fixture, 'payment_cancel_race_payment_query'),
    },
    tags: {
      name: 'payment_query',
      scenario: 'payment_cancel_race',
    },
    responseCallback: http.expectedStatuses(200, 404),
  });

  const paymentQueryOk = paymentQueryResponse.status === 200 || paymentQueryResponse.status === 404;
  racePaymentQuerySuccess.add(paymentQueryOk);

  if (paymentQueryResponse.status === 200 && orderCancelled) {
    const payments = paymentQueryResponse.json('data.payments');
    const statuses = Array.isArray(payments)
      ? payments.map((payment) => payment.status)
      : [];
    const unresolved = statuses.includes('REQUESTED') || statuses.includes('SUCCEEDED');
    if (unresolved) {
      raceUnresolvedAfterReconcileCount.add(1);
      if (shouldLogFailure(failureLogCount)) {
        logFailure(fixture, 'payment_cancel_race', 'unresolved_after_reconcile', paymentQueryResponse, {
          orderId: created.orderId,
          paymentStatuses: statuses.join(','),
          couponId,
        });
        failureLogCount += 1;
      }
    }
  }

  sleep(Math.random() * 0.2);
}
