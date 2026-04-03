import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

// =============================================================================
// 대기열 + 주문 부하 테스트
//
// 시나리오: 유저가 대기열 진입 → 토큰 발급 대기 → 주문 생성
// 목적: 주문 API의 p99 레이턴시 측정 및 대기열 시스템 동작 검증
//
// 실행: k6 run k6/queue-order-load-test.js
// =============================================================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MAX_USERS = __ENV.MAX_USERS ? parseInt(__ENV.MAX_USERS) : 9;

// Custom metrics
const orderDuration = new Trend('order_duration', true);
const queueWaitTime = new Trend('queue_wait_time', true);
const orderSuccess = new Counter('order_success');
const orderFailed = new Counter('order_failed');
const tokenTimeout = new Counter('token_timeout');
const failRate = new Rate('order_fail_rate');

export const options = {
    scenarios: {
        queue_order_flow: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5s', target: 20 },    // Warm-up
                { duration: '15s', target: 50 },   // Ramp-up
                { duration: '30s', target: 50 },   // Sustained peak
                { duration: '10s', target: 0 },    // Cool-down
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        order_fail_rate: ['rate<0.1'],
    },
};

export default function () {
    const userId = ((__VU - 1) % MAX_USERS) + 1;
    const loginId = `user${userId}`;
    const authHeaders = {
        'X-Loopers-LoginId': loginId,
        'X-Loopers-LoginPw': 'Password1!',
    };

    // 1. Enter queue
    const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
        headers: authHeaders,
    });

    const enterData = enterRes.json('data');
    if (!enterData) {
        orderFailed.add(1);
        failRate.add(true);
        return;
    }

    // 2. If already admitted, go straight to order
    if (enterData.status === 'ADMITTED') {
        placeOrder(authHeaders, userId);
        return;
    }

    // 3. Poll for admission (max 30s)
    const startWait = Date.now();
    const maxWaitMs = 30000;
    let admitted = false;

    while (Date.now() - startWait < maxWaitMs) {
        sleep(0.5);

        const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
            headers: authHeaders,
        });

        const posData = posRes.json('data');
        if (posData && posData.status === 'ADMITTED') {
            admitted = true;
            break;
        }
    }

    const waitTime = Date.now() - startWait;
    queueWaitTime.add(waitTime);

    if (!admitted) {
        tokenTimeout.add(1);
        failRate.add(true);
        return;
    }

    // 4. Place order
    placeOrder(authHeaders, userId);
}

function placeOrder(authHeaders, userId) {
    const productId = (userId % 5) + 1;
    const orderPayload = JSON.stringify({
        items: [{ productId: productId, quantity: 1 }],
    });

    const start = Date.now();
    const orderRes = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
        headers: Object.assign({}, authHeaders, {
            'Content-Type': 'application/json',
        }),
    });
    const duration = Date.now() - start;

    orderDuration.add(duration);

    const success = check(orderRes, {
        'order created': (r) => r.status === 201 || r.status === 200,
    });

    if (success) {
        orderSuccess.add(1);
        failRate.add(false);
    } else {
        orderFailed.add(1);
        failRate.add(true);
    }
}
