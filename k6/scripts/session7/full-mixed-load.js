import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders, getProductIdZipf } from '../../lib/helpers.js';

/**
 * 운영 전체 — 혼합 시나리오 부하 테스트 (10분)
 *
 * 실제 트래픽 패턴을 시뮬레이션: 조회 60% + 좋아요 15% + 주문 15% + 쿠폰 10%
 * 전체 시스템이 10분간 안정적으로 동작하는지 확인한다.
 *
 * 관찰 포인트:
 *   - 전체 에러율 < 0.1%
 *   - 상품 조회 p99 < 200ms
 *   - 좋아요 p99 < 200ms
 *   - 주문 생성 p99 < 500ms
 *   - HikariCP active connections (Grafana)
 *   - @Async 큐 크기 (Grafana)
 *   - Kafka Consumer Lag (Grafana)
 *
 * 실행:
 *   docker run --rm -i --network host \
 *     -e RUSH_COUPON_ID=1 \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/session7/full-mixed-load.js
 */

const RUSH_COUPON_ID = __ENV.RUSH_COUPON_ID || '1';

export const options = {
    scenarios: {
        mixed: {
            executor: 'ramping-vus',
            startVUs: 10,
            stages: [
                { duration: '1m', target: 50 },    // ramp-up
                { duration: '7m', target: 100 },   // sustained
                { duration: '1m', target: 50 },    // ramp-down
                { duration: '1m', target: 10 },    // cool-down
            ],
        },
    },
    thresholds: {
        'http_req_failed': ['rate<0.001'],
        'view_duration': ['p(99)<200'],
        'like_duration': ['p(99)<200'],
        'order_duration': ['p(99)<500'],
        'coupon_duration': ['p(99)<200'],
    },
};

const BASE = 'http://localhost:8080';

// 메트릭 분류
const viewDuration = new Trend('view_duration');
const likeDuration = new Trend('like_duration');
const orderDuration = new Trend('order_duration');
const couponDuration = new Trend('coupon_duration');

const viewCount = new Counter('view_count');
const likeCount = new Counter('like_count');
const orderCount = new Counter('order_count');
const couponCount = new Counter('coupon_count');
const errorCount = new Counter('error_count');

export default function () {
    const vuId = (__VU % 1000) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');
    const rand = Math.random();

    if (rand < 0.6) {
        // 60% — 상품 조회
        doProductView(HEADERS);
    } else if (rand < 0.75) {
        // 15% — 좋아요
        doLike(HEADERS);
    } else if (rand < 0.9) {
        // 15% — 주문 생성
        doOrder(HEADERS);
    } else {
        // 10% — 선착순 쿠폰
        doRushCoupon(HEADERS, vuId);
    }

    sleep(0.1);
}

function doProductView(headers) {
    const productId = getProductIdZipf(100);
    const start = Date.now();
    const res = http.get(`${BASE}/api/v1/products/${productId}`, { headers });
    viewDuration.add(Date.now() - start);

    if (res.status === 200) {
        viewCount.add(1);
    } else {
        errorCount.add(1);
    }
}

function doLike(headers) {
    const productId = Math.floor(Math.random() * 100) + 1;
    const start = Date.now();
    const res = http.post(`${BASE}/api/v1/products/${productId}/likes`,
        null, { headers });
    likeDuration.add(Date.now() - start);

    if (res.status === 200) {
        likeCount.add(1);
    } else {
        errorCount.add(1);
    }
}

function doOrder(headers) {
    const productId = Math.floor(Math.random() * 100) + 1;
    const start = Date.now();
    const res = http.post(`${BASE}/api/v1/orders`,
        JSON.stringify({ items: [{ productId, quantity: 1 }] }),
        { headers });
    orderDuration.add(Date.now() - start);

    if (res.status === 201) {
        orderCount.add(1);
    } else {
        errorCount.add(1);
    }
}

function doRushCoupon(headers, vuId) {
    const start = Date.now();
    const res = http.post(
        `${BASE}/api/v1/coupons/${RUSH_COUPON_ID}/rush-issue`,
        null, { headers });
    couponDuration.add(Date.now() - start);

    // 202, 400, 409 모두 정상 응답
    if (res.status === 200 || res.status === 202 || res.status === 400 || res.status === 409) {
        couponCount.add(1);
    } else {
        errorCount.add(1);
    }
}

export function handleSummary(data) {
    const views = data.metrics.view_count ? data.metrics.view_count.values.count : 0;
    const likes = data.metrics.like_count ? data.metrics.like_count.values.count : 0;
    const orders = data.metrics.order_count ? data.metrics.order_count.values.count : 0;
    const coupons = data.metrics.coupon_count ? data.metrics.coupon_count.values.count : 0;
    const errors = data.metrics.error_count ? data.metrics.error_count.values.count : 0;
    const total = views + likes + orders + coupons + errors;

    return {
        stdout: `
=== Full Mixed Load 결과 ===
  총 요청:      ${total}
  상품 조회:    ${views} (${(views/total*100).toFixed(1)}%)
  좋아요:       ${likes} (${(likes/total*100).toFixed(1)}%)
  주문 생성:    ${orders} (${(orders/total*100).toFixed(1)}%)
  선착순 쿠폰:  ${coupons} (${(coupons/total*100).toFixed(1)}%)
  에러:         ${errors} (에러율: ${(errors/total*100).toFixed(3)}%)

  → Grafana에서 확인:
    - HikariCP active connections
    - @Async queue size
    - Kafka Consumer Lag
    - Outbox PENDING count
`,
    };
}
