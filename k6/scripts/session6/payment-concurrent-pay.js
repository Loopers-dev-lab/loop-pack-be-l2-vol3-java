import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * 결제 분산락 동시성 테스트
 *
 * 1개 주문에 대해 10 VU가 동시에 결제를 시도한다.
 * 기대: 1건만 PG 호출, 나머지 9건은 PAYMENT_ALREADY_IN_PROGRESS (409)
 *
 * 사전 조건:
 *   - commerce-api 실행 중 (localhost:8080)
 *   - PG 시뮬레이터 실행 중
 *   - k6user1 계정 존재 (seed 데이터)
 *   - 상품/재고 존재 (seed 데이터)
 *
 * 실행:
 *   docker run --rm -i --network host \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/payment-concurrent-pay.js
 */

const BASE = 'http://localhost:8080';
const HEADERS = authHeaders('testuser', 'Test1234!');

const paymentSuccess = new Counter('payment_success');
const paymentRejected = new Counter('payment_rejected');
const paymentOtherFail = new Counter('payment_other_fail');

// ━━ setup: 상품 조회 → 주문 1건 생성 (모든 VU가 공유) ━━
export function setup() {
    // 재고가 있는 상품 조회
    const productRes = http.get(`${BASE}/api/v1/products?page=0&size=1`, { headers: HEADERS });
    if (productRes.status !== 200) {
        console.error('상품 조회 실패 — 테스트 중단. status=' + productRes.status);
        return { orderId: null };
    }
    const products = JSON.parse(productRes.body);
    const productId = products.data.content[0].productId;
    console.log('테스트 상품 선택. productId=' + productId);

    // 주문 생성
    const orderPayload = JSON.stringify({
        orderType: 'DIRECT',
        items: [{
            productId: productId,
            quantity: 1,
        }],
    });

    const orderRes = http.post(`${BASE}/api/v1/orders`, orderPayload, { headers: HEADERS });

    const orderCheck = check(orderRes, {
        'setup: 주문 생성 성공': (r) => r.status === 200 || r.status === 201,
    });

    if (!orderCheck) {
        console.error('주문 생성 실패 — 테스트 중단. status=' + orderRes.status + ', body=' + orderRes.body);
        return { orderId: null };
    }

    const body = JSON.parse(orderRes.body);
    const orderId = body.data.orderId;
    console.log('테스트 주문 생성 완료. orderId=' + orderId);

    return { orderId: orderId };
}

export const options = {
    scenarios: {
        concurrent_pay: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 10,
            maxDuration: '30s',
        },
    },
};

// ━━ default: 모든 VU가 동일 주문에 동시 결제 시도 ━━
export default function (data) {
    if (!data.orderId) {
        console.error('orderId가 없음 — setup 실패');
        return;
    }

    const paymentPayload = JSON.stringify({
        orderId: data.orderId,
        cardType: 'SAMSUNG',
        cardNo: '1234-5678-9012-3456',
    });

    const res = http.post(`${BASE}/api/v1/payments`, paymentPayload, { headers: HEADERS });

    if (res.status === 200) {
        paymentSuccess.add(1);
        console.log(`VU ${__VU}: 결제 성공 ✓`);
    } else if (res.status === 409) {
        paymentRejected.add(1);
        console.log(`VU ${__VU}: 분산락 차단 (409) ✓`);
    } else {
        paymentOtherFail.add(1);
        console.log(`VU ${__VU}: 기타 실패. status=${res.status}, body=${res.body}`);
    }
}

// ━━ teardown: 결과 요약 ━━
export function handleSummary(data) {
    const success = data.metrics.payment_success
        ? data.metrics.payment_success.values.count : 0;
    const rejected = data.metrics.payment_rejected
        ? data.metrics.payment_rejected.values.count : 0;
    const otherFail = data.metrics.payment_other_fail
        ? data.metrics.payment_other_fail.values.count : 0;
    const total = success + rejected + otherFail;

    console.log('\n━━━ 분산락 동시 결제 테스트 결과 ━━━');
    console.log('총 요청:        ' + total + '건');
    console.log('결제 성공:      ' + success + '건 (기대: 1건)');
    console.log('분산락 차단:    ' + rejected + '건 (기대: ' + (total - 1) + '건)');
    console.log('기타 실패:      ' + otherFail + '건 (기대: 0건)');
    console.log('');

    if (success === 1 && otherFail === 0) {
        console.log('✅ PASS — 분산락이 이중결제를 정확히 차단했습니다.');
    } else if (success > 1) {
        console.log('❌ FAIL — ' + success + '건 성공! 이중결제 발생!');
    } else if (success === 0) {
        console.log('⚠️  WARN — 성공 0건. 주문/재고 상태를 확인하세요.');
    }
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    return {};
}
