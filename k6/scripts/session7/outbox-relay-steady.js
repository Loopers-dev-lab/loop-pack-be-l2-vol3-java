import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * Step 2 — Outbox Relay 안정성 테스트 (5분 Steady)
 *
 * 주문 생성 → outbox_event INSERT → Relay → Kafka → Consumer → product_metrics
 * 전체 파이프라인이 5분간 안정적으로 동작하는지 확인한다.
 *
 * 관찰 포인트:
 *   - outbox PENDING 적체 < 10건 (SELECT status, COUNT(*) FROM outbox_event GROUP BY status)
 *   - PUBLISHED 전환율 > 99%
 *   - product_metrics.sales_count가 주문 건수와 일치
 *
 * 실행:
 *   docker run --rm -i --network host \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/session7/outbox-relay-steady.js
 */
export const options = {
    scenarios: {
        order_steady: {
            executor: 'constant-vus',
            vus: 20,
            duration: '5m',
        },
    },
    thresholds: {
        'order_success': ['count>100'],
        'http_req_duration': ['p(95)<300'],
    },
};

const BASE = 'http://localhost:8080';

const orderSuccess = new Counter('order_success');
const orderFail = new Counter('order_fail');
const orderDuration = new Trend('order_duration');

export default function () {
    const vuId = (__VU % 1000) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');
    const productId = Math.floor(Math.random() * 100) + 1;

    const payload = JSON.stringify({
        orderType: 'DIRECT',
        items: [{ productId, quantity: 1 }],
    });

    const start = Date.now();
    const res = http.post(`${BASE}/api/v1/orders`, payload, { headers: HEADERS });
    orderDuration.add(Date.now() - start);

    if (res.status === 200) {
        orderSuccess.add(1);

        // 30% 확률로 주문 취소 (ORDER_CANCELLED 이벤트 발생)
        if (Math.random() < 0.3) {
            try {
                const orderId = JSON.parse(res.body).data.orderId;
                sleep(0.5);
                http.del(`${BASE}/api/v1/orders/${orderId}`, null, { headers: HEADERS });
            } catch (e) { /* ignore */ }
        }
    } else {
        orderFail.add(1);
    }

    sleep(1);
}
