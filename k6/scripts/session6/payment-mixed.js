import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders, getProductIdZipf, checkResponse } from '../../lib/helpers.js';

/**
 * Phase 2 — Mixed 부하 테스트 (결제 + 상품조회 혼합)
 *
 * 결제 장애가 상품조회 API에 전파되는지 측정한다.
 * Phase 1에서는 TX 분리로 DB 커넥션 풀을 보호하지만,
 * PG 장애 시 스레드 점유로 인한 간접 영향을 관찰한다.
 *
 * 실행:
 *   docker run --rm -i --network host \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/payment-mixed.js
 */
export const options = {
    scenarios: {
        payment: {
            executor: 'constant-vus',
            vus: 30,
            duration: '1m',
            exec: 'paymentFlow',
        },
        productQuery: {
            executor: 'constant-vus',
            vus: 20,
            duration: '1m',
            exec: 'productQuery',
        },
    },
    thresholds: {
        'product_query_duration': ['p(95)<200'],   // 상품조회는 200ms 이내 유지?
        'http_req_duration': ['p(99)<10000'],
    },
};

const BASE = 'http://localhost:8080';

const paymentSuccess = new Counter('payment_success');
const paymentFailure = new Counter('payment_failure');
const paymentDuration = new Trend('payment_duration');
const productQueryDuration = new Trend('product_query_duration');

export function paymentFlow() {
    // VU별 다른 유저 사용 (PENDING 제한 회피)
    const vuId = (__VU % 50) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');

    const orderPayload = JSON.stringify({
        orderType: 'DIRECT',
        items: [{
            productId: Math.floor(Math.random() * 100) + 1,
            quantity: 1,
        }],
    });

    const orderRes = http.post(`${BASE}/api/v1/orders`, orderPayload, { headers: HEADERS });
    if (orderRes.status !== 200 && orderRes.status !== 201) {
        paymentFailure.add(1);
        sleep(0.5);
        return;
    }

    let orderId;
    try {
        orderId = JSON.parse(orderRes.body).data.orderId;
    } catch (e) {
        paymentFailure.add(1);
        sleep(0.5);
        return;
    }

    const paymentPayload = JSON.stringify({
        orderId: orderId,
        cardType: ['SAMSUNG', 'KB', 'HYUNDAI'][Math.floor(Math.random() * 3)],
        cardNo: '1234-5678-9012-3456',
    });

    const start = Date.now();
    const paymentRes = http.post(`${BASE}/api/v1/payments`, paymentPayload, { headers: HEADERS });
    paymentDuration.add(Date.now() - start);

    if (paymentRes.status === 200) {
        paymentSuccess.add(1);
    } else {
        paymentFailure.add(1);
    }

    sleep(0.5 + Math.random() * 1.0);
}

export function productQuery() {
    const vuId = (__VU % 50) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');

    const productId = getProductIdZipf(100);
    const start = Date.now();
    const res = http.get(`${BASE}/api/v1/products/${productId}`, { headers: HEADERS });
    productQueryDuration.add(Date.now() - start);

    check(res, checkResponse(res, 'product-detail'));

    sleep(0.1);
}

function safeGet(obj) {
    if (!obj || !obj.values) return { count: 0, med: 0, 'p(95)': 0, max: 0 };
    return obj.values;
}

export function handleSummary(data) {
    var ps = safeGet(data.metrics.payment_success);
    var pf = safeGet(data.metrics.payment_failure);
    var pd = safeGet(data.metrics.payment_duration);
    var pq = safeGet(data.metrics.product_query_duration);
    var total = (ps.count || 0) + (pf.count || 0);
    var success = ps.count || 0;
    var rate = total > 0 ? (success / total * 100).toFixed(1) : '0.0';

    console.log('\n━━━ Phase 2 Mixed 결과 ━━━');
    console.log('[결제] 총: ' + total + '건, 성공률: ' + rate + '%');
    console.log('[결제] p50: ' + (pd.med ? pd.med.toFixed(0) : '-') + 'ms');
    console.log('[결제] p95: ' + (pd['p(95)'] ? pd['p(95)'].toFixed(0) : '-') + 'ms');
    console.log('[상품] p50: ' + (pq.med ? pq.med.toFixed(0) : '-') + 'ms');
    console.log('[상품] p95: ' + (pq['p(95)'] ? pq['p(95)'].toFixed(0) : '-') + 'ms');
    console.log('발제: 결제 장애가 상품조회에 전파되는가? → p95 비교');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    return {};
}
