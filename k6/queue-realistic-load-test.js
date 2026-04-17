import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate, Gauge } from 'k6/metrics';

// =============================================================================
// 현실적 혼합 트래픽 부하 테스트
//
// 시나리오:
//   - 유저 100명 (1인 1VU, 토큰 경합 없음)
//   - 혼합 트래픽: 상품 조회 70% + 대기열+주문 30%
//   - HikariCP active 커넥션 모니터링
//
// 목적: 배치 크기 8명 설정에서 커넥션 풀 사용률 검증
//
// 실행: k6 run k6/queue-realistic-load-test.js
// =============================================================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACTUATOR_URL = __ENV.ACTUATOR_URL || 'http://localhost:8081';
const MAX_USERS = 100;

// Custom metrics
const orderDuration = new Trend('order_duration', true);
const productViewDuration = new Trend('product_view_duration', true);
const queueWaitTime = new Trend('queue_wait_time', true);
const orderSuccess = new Counter('order_success');
const orderFailed = new Counter('order_failed');
const productViews = new Counter('product_views');
const failRate = new Rate('order_fail_rate');

export const options = {
    scenarios: {
        // 혼합 트래픽: 상품 조회 + 주문
        mixed_traffic: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5s', target: 30 },    // Warm-up
                { duration: '10s', target: 80 },   // Ramp-up
                { duration: '30s', target: 80 },   // Sustained peak
                { duration: '5s', target: 0 },     // Cool-down
            ],
        },
        // HikariCP 모니터링 (1초 주기)
        monitor: {
            executor: 'constant-arrival-rate',
            rate: 1,
            timeUnit: '1s',
            duration: '50s',
            preAllocatedVUs: 1,
            maxVUs: 1,
            exec: 'monitorHikari',
        },
    },
    thresholds: {
        order_duration: ['p(95)<500', 'p(99)<1000'],
        order_fail_rate: ['rate<0.05'],
    },
};

// --- 혼합 트래픽 시나리오 ---
export default function () {
    const userId = ((__VU - 1) % MAX_USERS) + 1;
    const paddedId = String(userId).padStart(3, '0');
    const loginId = `lu${paddedId}`;
    const authHeaders = {
        'X-Loopers-LoginId': loginId,
        'X-Loopers-LoginPw': 'Password1!',
    };

    // 70% 상품 조회, 30% 주문
    if (Math.random() < 0.7) {
        viewProduct(authHeaders);
    } else {
        orderFlow(authHeaders, userId);
    }

    sleep(0.1 + Math.random() * 0.3); // 100~400ms think time
}

function viewProduct(authHeaders) {
    const productId = Math.floor(Math.random() * 5) + 1;
    const start = Date.now();
    const res = http.get(`${BASE_URL}/api/v1/products/${productId}`, {
        headers: authHeaders,
        tags: { name: 'product_view' },
    });
    productViewDuration.add(Date.now() - start);
    productViews.add(1);
}

function orderFlow(authHeaders, userId) {
    // 1. Enter queue
    const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
        headers: authHeaders,
        tags: { name: 'queue_enter' },
    });

    const enterData = enterRes.json('data');
    if (!enterData) {
        orderFailed.add(1);
        failRate.add(true);
        return;
    }

    if (enterData.status === 'ADMITTED') {
        placeOrder(authHeaders, userId);
        return;
    }

    // 2. Poll for admission (max 60s)
    const startWait = Date.now();
    let admitted = false;

    while (Date.now() - startWait < 60000) {
        sleep(1);
        const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
            headers: authHeaders,
            tags: { name: 'queue_position' },
        });
        const posData = posRes.json('data');
        if (posData && posData.status === 'ADMITTED') {
            admitted = true;
            break;
        }
    }

    queueWaitTime.add(Date.now() - startWait);

    if (!admitted) {
        orderFailed.add(1);
        failRate.add(true);
        return;
    }

    placeOrder(authHeaders, userId);
}

function placeOrder(authHeaders, userId) {
    const productId = (userId % 5) + 1;
    const orderPayload = JSON.stringify({
        items: [{ productId: productId, quantity: 1 }],
    });

    const start = Date.now();
    const orderRes = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
        headers: Object.assign({}, authHeaders, { 'Content-Type': 'application/json' }),
        tags: { name: 'order_create' },
    });
    orderDuration.add(Date.now() - start);

    const success = check(orderRes, {
        'order created (201)': (r) => r.status === 201,
    });

    if (success) {
        orderSuccess.add(1);
        failRate.add(false);
    } else {
        orderFailed.add(1);
        failRate.add(true);
    }
}

// --- HikariCP 모니터링 시나리오 ---
export function monitorHikari() {
    const res = http.get(`${ACTUATOR_URL}/actuator/prometheus`, {
        tags: { name: 'actuator' },
    });

    if (res.status !== 200) return;

    const body = res.body;

    const activeMatch = body.match(/hikaricp_connections_active\{[^}]*\}\s+([\d.]+)/);
    const pendingMatch = body.match(/hikaricp_connections_pending\{[^}]*\}\s+([\d.]+)/);
    const totalMatch = body.match(/hikaricp_connections\{[^}]*pool="mysql-main-pool"[^}]*\}\s+([\d.]+)/);

    const active = activeMatch ? parseFloat(activeMatch[1]) : 0;
    const pending = pendingMatch ? parseFloat(pendingMatch[1]) : 0;
    const total = totalMatch ? parseFloat(totalMatch[1]) : 40;

    console.log(`[HikariCP] active=${active}/${total} pending=${pending} usage=${(active/total*100).toFixed(1)}%`);
}
