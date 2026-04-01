import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * Phase 2 — Baseline 부하 테스트 (50 VU × 3분)
 *
 * Phase 1 "날것" 상태에서의 결제 성능 기준선을 수립한다.
 * PG 시뮬레이터 40% 실패율 환경에서 성공률, 응답 시간을 측정.
 *
 * 실행:
 *   docker run --rm -i --network host \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/payment-baseline.js
 */
export const options = {
    scenarios: {
        baseline: {
            executor: 'constant-vus',
            vus: 50,
            duration: '1m',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<5000'],      // Phase 1: timeout 없음 → 넉넉하게
    },
};

const BASE = 'http://localhost:8080';
const PG_BASE = 'http://localhost:8082';

const paymentSuccess = new Counter('payment_success');
const paymentFailure = new Counter('payment_failure');
const paymentDuration = new Trend('payment_duration');

export default function () {
    // VU별 다른 유저 사용 (PENDING 제한 회피)
    const vuId = (__VU % 50) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');

    // 1. 주문 생성 (DIRECT)
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
        const body = JSON.parse(orderRes.body);
        orderId = body.data.orderId;
    } catch (e) {
        paymentFailure.add(1);
        sleep(0.5);
        return;
    }

    // 2. 결제 요청
    const paymentPayload = JSON.stringify({
        orderId: orderId,
        cardType: ['SAMSUNG', 'KB', 'HYUNDAI'][Math.floor(Math.random() * 3)],
        cardNo: '1234-5678-9012-3456',
    });

    const start = Date.now();
    const paymentRes = http.post(`${BASE}/api/v1/payments`, paymentPayload, { headers: HEADERS });
    const elapsed = Date.now() - start;

    paymentDuration.add(elapsed);

    const isSuccess = check(paymentRes, {
        'payment status 200': (r) => r.status === 200,
        'payment has transactionKey': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.transactionKey;
            } catch (e) {
                return false;
            }
        },
    });

    if (isSuccess) {
        paymentSuccess.add(1);
    } else {
        paymentFailure.add(1);
    }

    sleep(0.5 + Math.random() * 1.0); // 0.5~1.5초 간격
}

function safeGet(obj) {
    if (!obj || !obj.values) return { count: 0, med: 0, 'p(95)': 0, max: 0 };
    return obj.values;
}

export function handleSummary(data) {
    var ps = safeGet(data.metrics.payment_success);
    var pf = safeGet(data.metrics.payment_failure);
    var pd = safeGet(data.metrics.payment_duration);
    var total = (ps.count || 0) + (pf.count || 0);
    var success = ps.count || 0;
    var rate = total > 0 ? (success / total * 100).toFixed(1) : '0.0';

    console.log('\n━━━ Phase 2 Baseline 결과 ━━━');
    console.log('총 요청: ' + total + '건');
    console.log('성공: ' + success + '건 (' + rate + '%)');
    console.log('실패: ' + (total - success) + '건');
    console.log('p50: ' + (pd.med ? pd.med.toFixed(0) : '-') + 'ms');
    console.log('p95: ' + (pd['p(95)'] ? pd['p(95)'].toFixed(0) : '-') + 'ms');
    console.log('max: ' + (pd.max ? pd.max.toFixed(0) : '-') + 'ms');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    return {};
}
