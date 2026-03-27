import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * Step 1 — 주문 생성 이벤트 격리 테스트
 *
 * 주문 생성 후 AFTER_COMMIT 리스너(로깅, 장바구니 복원 등)가
 * 실패하더라도 주문 자체는 100% 성공해야 한다.
 *
 * 관찰 포인트:
 *   - 주문 생성 성공률 100%
 *   - AFTER_COMMIT 로그가 출력되는지 앱 로그 확인
 *   - coupon_pending_actions에 CONFIRM 레코드 생성 확인
 *
 * 실행:
 *   docker run --rm -i --network host \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/session7/event-order-isolation.js
 */
export const options = {
    scenarios: {
        order_load: {
            executor: 'constant-vus',
            vus: 30,
            duration: '2m',
        },
    },
    thresholds: {
        'order_success_rate': ['rate>0.99'],
        'order_duration': ['p(95)<200'],
    },
};

const BASE = 'http://localhost:8080';

const orderSuccess = new Counter('order_success');
const orderFail = new Counter('order_fail');
const orderDuration = new Trend('order_duration');
const orderSuccessRate = new Counter('order_success_rate');

export default function () {
    const vuId = (__VU % 1000) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');
    const productId = Math.floor(Math.random() * 100) + 1;

    const payload = JSON.stringify({
        items: [{ productId, quantity: 1 }],
    });

    const start = Date.now();
    const res = http.post(`${BASE}/api/v1/orders`, payload, { headers: HEADERS });
    orderDuration.add(Date.now() - start);

    const success = check(res, {
        'order status 201': (r) => r.status === 201,
        'order has orderId': (r) => {
            try {
                return JSON.parse(r.body).data.orderId > 0;
            } catch (e) { return false; }
        },
    });

    if (success) {
        orderSuccess.add(1);
    } else {
        orderFail.add(1);
    }

    sleep(0.5);
}
