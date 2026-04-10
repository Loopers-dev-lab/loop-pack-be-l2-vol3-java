import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * Optimistic Stock 전용 부하 테스트
 *
 * 3가지 시나리오를 동시 실행:
 *   1. 재고 경합 (stock-contention): 재고 5개 상품에 20 VU 동시 결제 → 오버셀 방지 검증
 *   2. Polling/고아 복구 (polling-recovery): 결제 후 2분 대기 → DB에서 최종 상태 확인
 *   3. 주문 대기 후 결제 (delayed-payment): 주문 생성 → 5초 대기 → 결제 → 재고 부족 발생 확인
 *
 * 사전 준비:
 *   1. 재고 5개짜리 상품 생성 (productId 기록)
 *   2. k6 유저 50명 생성 완료
 *
 * 실행:
 *   # 재고 경합 상품 생성 (관리자 API)
 *   curl -X POST http://localhost:8080/api-admin/v1/products \
 *     -H "Content-Type: application/json" -H "X-Loopers-Ldap: loopers.admin" \
 *     -d '{"productName":"경합테스트상품","brandId":1,"price":10000,"description":"재고5개","initialStock":5}'
 *   # 반환된 productId를 LOW_STOCK_PRODUCT_ID에 설정
 *
 *   ~/k6 run k6/scripts/payment-optimistic-stock.js
 */

// ━━━ 설정 ━━━
const BASE = 'http://localhost:8080';
const LOW_STOCK_PRODUCT_ID = __ENV.LOW_STOCK_PID || 101;  // 재고 5개 상품 ID (환경변수로 주입)
const DB_HOST = __ENV.DB_HOST || '127.0.0.1';

export const options = {
    scenarios: {
        // 시나리오 1: 재고 경합 — 20 VU가 동시에 재고 5개 상품 결제
        stockContention: {
            executor: 'shared-iterations',
            vus: 20,
            iterations: 20,
            maxDuration: '2m',
            exec: 'stockContentionFlow',
        },
        // 시나리오 2: 일반 결제 후 Polling 복구 대기
        pollingRecovery: {
            executor: 'constant-vus',
            vus: 10,
            duration: '30s',
            exec: 'pollingRecoveryFlow',
            startTime: '0s',
        },
        // 시나리오 3: 주문 대기 후 결제 (재고 부족 발생 확인)
        delayedPayment: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 10,
            maxDuration: '2m',
            exec: 'delayedPaymentFlow',
            startTime: '5s',  // 시나리오 1이 재고를 먼저 소진하도록 약간 지연
        },
    },
};

// ━━━ 커스텀 메트릭 ━━━
const contentionSuccess = new Counter('contention_success');
const contentionStockFail = new Counter('contention_stock_not_enough');
const contentionOtherFail = new Counter('contention_other_fail');
const contentionDuration = new Trend('contention_duration');

const pollingSuccess = new Counter('polling_payment_success');
const pollingFailure = new Counter('polling_payment_failure');

const delayedSuccess = new Counter('delayed_success');
const delayedStockFail = new Counter('delayed_stock_not_enough');
const delayedOtherFail = new Counter('delayed_other_fail');

// ━━━ 시나리오 1: 재고 경합 ━━━
export function stockContentionFlow() {
    const vuId = (__VU % 50) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');

    // 1. 주문 생성 (재고 5개 상품)
    const orderPayload = JSON.stringify({
        orderType: 'DIRECT',
        items: [{ productId: parseInt(LOW_STOCK_PRODUCT_ID), quantity: 1 }],
    });

    const orderRes = http.post(`${BASE}/api/v1/orders`, orderPayload, { headers: HEADERS });

    if (orderRes.status !== 200 && orderRes.status !== 201) {
        // 낙관적 재고 확인에서 실패 (재고 소진)
        const body = safeParseBody(orderRes);
        if (body && body.meta && body.meta.errorCode === 'STOCK_NOT_ENOUGH') {
            contentionStockFail.add(1);
        } else {
            contentionOtherFail.add(1);
        }
        return;
    }

    const orderId = safeGetOrderId(orderRes);
    if (!orderId) { contentionOtherFail.add(1); return; }

    // 2. 결제 요청 (여기서 CAS hold 경합 발생)
    const paymentPayload = JSON.stringify({
        orderId: orderId,
        cardType: 'SAMSUNG',
        cardNo: '1234-5678-9012-3456',
    });

    const start = Date.now();
    const paymentRes = http.post(`${BASE}/api/v1/payments`, paymentPayload, { headers: HEADERS });
    contentionDuration.add(Date.now() - start);

    if (paymentRes.status === 200) {
        contentionSuccess.add(1);
    } else {
        const body = safeParseBody(paymentRes);
        if (body && body.meta && body.meta.errorCode === 'STOCK_NOT_ENOUGH') {
            contentionStockFail.add(1);
        } else {
            contentionOtherFail.add(1);
        }
    }
}

// ━━━ 시나리오 2: Polling 복구 검증 ━━━
export function pollingRecoveryFlow() {
    const vuId = (__VU % 50) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');

    // 일반 상품으로 주문+결제 (재고 충분)
    const productId = Math.floor(Math.random() * 100) + 1;
    const orderPayload = JSON.stringify({
        orderType: 'DIRECT',
        items: [{ productId: productId, quantity: 1 }],
    });

    const orderRes = http.post(`${BASE}/api/v1/orders`, orderPayload, { headers: HEADERS });
    if (orderRes.status !== 200 && orderRes.status !== 201) {
        pollingFailure.add(1);
        sleep(0.5);
        return;
    }

    const orderId = safeGetOrderId(orderRes);
    if (!orderId) { pollingFailure.add(1); return; }

    const paymentPayload = JSON.stringify({
        orderId: orderId,
        cardType: ['SAMSUNG', 'KB', 'HYUNDAI'][Math.floor(Math.random() * 3)],
        cardNo: '1234-5678-9012-3456',
    });

    const paymentRes = http.post(`${BASE}/api/v1/payments`, paymentPayload, { headers: HEADERS });

    if (paymentRes.status === 200) {
        pollingSuccess.add(1);
    } else {
        pollingFailure.add(1);
    }

    sleep(0.5 + Math.random() * 1.0);
}

// ━━━ 시나리오 3: 주문 대기 후 결제 ━━━
export function delayedPaymentFlow() {
    const vuId = (__VU % 50) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');

    // 1. 주문 생성 (재고 5개 상품)
    const orderPayload = JSON.stringify({
        orderType: 'DIRECT',
        items: [{ productId: parseInt(LOW_STOCK_PRODUCT_ID), quantity: 1 }],
    });

    const orderRes = http.post(`${BASE}/api/v1/orders`, orderPayload, { headers: HEADERS });

    if (orderRes.status !== 200 && orderRes.status !== 201) {
        const body = safeParseBody(orderRes);
        if (body && body.meta && body.meta.errorCode === 'STOCK_NOT_ENOUGH') {
            delayedStockFail.add(1);
        } else {
            delayedOtherFail.add(1);
        }
        return;
    }

    const orderId = safeGetOrderId(orderRes);
    if (!orderId) { delayedOtherFail.add(1); return; }

    // 2. 5초 대기 (다른 VU가 먼저 결제 완료하도록)
    sleep(5);

    // 3. 결제 요청 (재고 소진되었을 수 있음)
    const paymentPayload = JSON.stringify({
        orderId: orderId,
        cardType: 'KB',
        cardNo: '1234-5678-9012-3456',
    });

    const paymentRes = http.post(`${BASE}/api/v1/payments`, paymentPayload, { headers: HEADERS });

    if (paymentRes.status === 200) {
        delayedSuccess.add(1);
    } else {
        const body = safeParseBody(paymentRes);
        if (body && body.meta && body.meta.errorCode === 'STOCK_NOT_ENOUGH') {
            delayedStockFail.add(1);
        } else {
            delayedOtherFail.add(1);
        }
    }
}

// ━━━ 유틸 ━━━
function safeParseBody(res) {
    try { return JSON.parse(res.body); } catch (e) { return null; }
}

function safeGetOrderId(res) {
    try { return JSON.parse(res.body).data.orderId; } catch (e) { return null; }
}

// ━━━ 결과 출력 ━━━
function sg(obj) {
    if (!obj || !obj.values) return { count: 0 };
    return obj.values;
}

export function handleSummary(data) {
    var m = data.metrics;

    var cSuccess = sg(m.contention_success).count || 0;
    var cStock = sg(m.contention_stock_not_enough).count || 0;
    var cOther = sg(m.contention_other_fail).count || 0;

    var pSuccess = sg(m.polling_payment_success).count || 0;
    var pFail = sg(m.polling_payment_failure).count || 0;

    var dSuccess = sg(m.delayed_success).count || 0;
    var dStock = sg(m.delayed_stock_not_enough).count || 0;
    var dOther = sg(m.delayed_other_fail).count || 0;

    console.log('\n━━━ Optimistic Stock 테스트 결과 ━━━');
    console.log('');
    console.log('[1] 재고 경합 (상품 재고 5개, 20 VU 동시)');
    console.log('  성공(결제완료): ' + cSuccess + '건');
    console.log('  STOCK_NOT_ENOUGH: ' + cStock + '건');
    console.log('  기타 실패: ' + cOther + '건');
    console.log('  오버셀 검증: 성공 <= 5 → ' + (cSuccess <= 5 ? '✅ PASS' : '❌ FAIL (오버셀!)'));
    console.log('');
    console.log('[2] Polling 복구 (일반 상품, 30초)');
    console.log('  결제 성공: ' + pSuccess + '건');
    console.log('  결제 실패: ' + pFail + '건');
    console.log('  → k6 종료 후 2분 대기 후 DB에서 REQUESTED→SUCCESS/FAILED 전이 확인');
    console.log('');
    console.log('[3] 주문 대기 후 결제 (재고 5개 상품, 5초 대기)');
    console.log('  성공(결제완료): ' + dSuccess + '건');
    console.log('  STOCK_NOT_ENOUGH: ' + dStock + '건');
    console.log('  기타 실패: ' + dOther + '건');
    console.log('  → 대기 중 재고 소진 시 STOCK_NOT_ENOUGH 발생 확인');
    console.log('');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    return {};
}
