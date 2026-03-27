import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { authHeaders, getProductIdZipf } from '../../lib/helpers.js';

/**
 * Step 2 — Kafka Consumer Lag 모니터링 하 혼합 부하
 *
 * 좋아요 + 조회 + 주문을 혼합하여 catalog-events, order-events 동시 발행.
 * Consumer가 따라가는지 Lag을 Grafana에서 확인한다.
 *
 * 관찰 포인트:
 *   - Kafka Consumer Lag (Grafana: kafka_consumer_fetch_manager_records_lag_max)
 *   - product_metrics에 like_count, view_count, sales_count 갱신 확인
 *   - event_log에 FAILED 건수 0건
 *
 * 실행:
 *   docker run --rm -i --network host \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/session7/kafka-consumer-lag.js
 */
export const options = {
    scenarios: {
        mixed: {
            executor: 'constant-vus',
            vus: 50,
            duration: '5m',
        },
    },
    thresholds: {
        'http_req_failed': ['rate<0.01'],
    },
};

const BASE = 'http://localhost:8080';

const likeEvents = new Counter('like_events');
const viewEvents = new Counter('view_events');
const orderEvents = new Counter('order_events');

export default function () {
    const vuId = (__VU % 1000) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');
    const rand = Math.random();

    if (rand < 0.4) {
        // 40% — 상품 조회 (ProductViewedEvent → catalog-events)
        const productId = getProductIdZipf(100);
        const res = http.get(`${BASE}/api/v1/products/${productId}`, { headers: HEADERS });
        check(res, { 'view 200': (r) => r.status === 200 });
        viewEvents.add(1);

    } else if (rand < 0.7) {
        // 30% — 좋아요 (ProductLikedEvent → catalog-events)
        const productId = Math.floor(Math.random() * 100) + 1;
        http.post(`${BASE}/api/v1/products/${productId}/likes`,
            null,
            { headers: HEADERS });
        likeEvents.add(1);

    } else {
        // 30% — 주문 생성 (OrderCreatedEvent → order-events via Outbox)
        const productId = Math.floor(Math.random() * 100) + 1;
        const res = http.post(`${BASE}/api/v1/orders`,
            JSON.stringify({ items: [{ productId, quantity: 1 }] }),
            { headers: HEADERS });
        if (res.status === 201) orderEvents.add(1);
    }

    sleep(0.1);
}
