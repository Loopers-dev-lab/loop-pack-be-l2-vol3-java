import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * Phase 2 — Spike 부하 테스트 (10→200→10 VU 급증)
 *
 * 갑작스러운 트래픽 급증 시 DB 커넥션 풀 고갈,
 * PG 응답 지연 누적, 스레드 점유 등의 문제를 체감한다.
 *
 * 실행:
 *   docker run --rm -i --network host \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/payment-spike.js
 */
export const options = {
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 10,
            stages: [
                { duration: '15s', target: 10 },   // warm-up
                { duration: '5s',  target: 100 },   // spike up
                { duration: '30s', target: 100 },   // sustain spike
                { duration: '5s',  target: 10 },    // spike down
                { duration: '15s', target: 10 },    // cool-down
            ],
        },
    },
    thresholds: {
        'http_req_duration': ['p(99)<10000'],
    },
};

const BASE = 'http://localhost:8080';

const paymentSuccess = new Counter('payment_success');
const paymentFailure = new Counter('payment_failure');
const paymentDuration = new Trend('payment_duration');

export default function () {
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
        sleep(0.3);
        return;
    }

    let orderId;
    try {
        orderId = JSON.parse(orderRes.body).data.orderId;
    } catch (e) {
        paymentFailure.add(1);
        sleep(0.3);
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

    sleep(0.2 + Math.random() * 0.5);
}

function safeGet(obj) {
    if (!obj || !obj.values) return { count: 0, med: 0, 'p(95)': 0, 'p(99)': 0, max: 0 };
    return obj.values;
}

export function handleSummary(data) {
    var ps = safeGet(data.metrics.payment_success);
    var pf = safeGet(data.metrics.payment_failure);
    var pd = safeGet(data.metrics.payment_duration);
    var total = (ps.count || 0) + (pf.count || 0);
    var success = ps.count || 0;
    var rate = total > 0 ? (success / total * 100).toFixed(1) : '0.0';

    console.log('\n━━━ Phase 2 Spike 결과 ━━━');
    console.log('총 요청: ' + total + '건, 성공률: ' + rate + '%');
    console.log('p50: ' + (pd.med ? pd.med.toFixed(0) : '-') + 'ms');
    console.log('p95: ' + (pd['p(95)'] ? pd['p(95)'].toFixed(0) : '-') + 'ms');
    console.log('p99: ' + (pd['p(99)'] ? pd['p(99)'].toFixed(0) : '-') + 'ms');
    console.log('max: ' + (pd.max ? pd.max.toFixed(0) : '-') + 'ms');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    return {};
}
