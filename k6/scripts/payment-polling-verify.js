import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { authHeaders } from '../lib/helpers.js';

/**
 * Polling/고아 Payment 복구 검증 테스트
 *
 * PG 40% 실패 환경에서 결제 요청 후, Polling 스케줄러가
 * REQUESTED 상태 Payment를 정상 해소하는지 end-to-end 검증한다.
 *
 * 흐름:
 *   Phase 1 (30초): 결제 요청 생성 → REQUESTED Payment 다수 발생
 *   Phase 2 (2분 대기): Polling 스케줄러가 REQUESTED를 처리할 시간 확보
 *   Phase 3: DB 직접 조회로 최종 상태 확인
 *     - REQUESTED → 0건 (전량 해소)
 *     - Payment SUCCESS = Order PAID (정합성)
 *     - reserved = 0 (재고 완전 해제)
 *     - 고아 Payment (TK=null) → 0건
 *
 * 실행:
 *   # DB 초기화
 *   mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers -e "
 *     DELETE FROM payment_compensation; DELETE FROM payment;
 *     DELETE FROM order_cart_restore; DELETE FROM order_items; DELETE FROM orders;
 *     UPDATE product_stocks SET reserved = 0;"
 *
 *   # Phase 1: 결제 부하
 *   ~/k6 run k6/scripts/payment-polling-verify.js
 *
 *   # Phase 2: 2분 대기 (Polling 동작)
 *   echo "2분 대기 (Polling 복구 중)..." && sleep 120
 *
 *   # Phase 3: DB 검증
 *   mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers -e "
 *     SELECT '=== REQUESTED 잔류 ===' as info;
 *     SELECT COUNT(*) as requested FROM payment WHERE status = 'REQUESTED';
 *     SELECT '=== 고아 Payment (TK=null + REQUESTED) ===' as info;
 *     SELECT COUNT(*) as orphan FROM payment WHERE transaction_key IS NULL AND status = 'REQUESTED';
 *     SELECT '=== 정합성: SUCCESS = PAID ===' as info;
 *     SELECT (SELECT COUNT(*) FROM payment WHERE status='SUCCESS') as payment_success,
 *            (SELECT COUNT(*) FROM orders WHERE status='PAID') as order_paid;
 *     SELECT '=== 재고 reserved ===' as info;
 *     SELECT SUM(reserved) as total_reserved FROM product_stocks;
 *     SELECT '=== 보상 테이블 ===' as info;
 *     SELECT COUNT(*) as compensation FROM payment_compensation;
 *     SELECT '=== Payment 상태 분포 ===' as info;
 *     SELECT status, COUNT(*) as cnt FROM payment GROUP BY status;
 *     SELECT '=== Order 상태 분포 ===' as info;
 *     SELECT status, COUNT(*) as cnt FROM orders GROUP BY status;"
 */

export const options = {
    scenarios: {
        paymentLoad: {
            executor: 'constant-vus',
            vus: 30,
            duration: '30s',
            exec: 'paymentFlow',
        },
    },
};

const BASE = 'http://localhost:8080';

const paySuccess = new Counter('pay_success');
const payFail = new Counter('pay_fail');
const payTimeout = new Counter('pay_timeout');

export function paymentFlow() {
    const vuId = (__VU % 50) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');

    // 1. 주문 생성
    const orderPayload = JSON.stringify({
        orderType: 'DIRECT',
        items: [{
            productId: Math.floor(Math.random() * 100) + 1,
            quantity: 1,
        }],
    });

    const orderRes = http.post(`${BASE}/api/v1/orders`, orderPayload, { headers: HEADERS });
    if (orderRes.status !== 200 && orderRes.status !== 201) {
        payFail.add(1);
        sleep(0.5);
        return;
    }

    let orderId;
    try {
        orderId = JSON.parse(orderRes.body).data.orderId;
    } catch (e) {
        payFail.add(1);
        sleep(0.5);
        return;
    }

    // 2. 결제 요청
    const paymentPayload = JSON.stringify({
        orderId: orderId,
        cardType: ['SAMSUNG', 'KB', 'HYUNDAI'][Math.floor(Math.random() * 3)],
        cardNo: '1234-5678-9012-3456',
    });

    const paymentRes = http.post(`${BASE}/api/v1/payments`, paymentPayload, { headers: HEADERS });

    if (paymentRes.status === 200) {
        paySuccess.add(1);
    } else {
        // PG 타임아웃 여부 확인
        const body = safeParseBody(paymentRes);
        if (body && body.meta && body.meta.errorCode === 'PAYMENT_PG_TIMEOUT') {
            payTimeout.add(1);
        } else {
            payFail.add(1);
        }
    }

    sleep(0.3 + Math.random() * 0.7);
}

function safeParseBody(res) {
    try { return JSON.parse(res.body); } catch (e) { return null; }
}

function sg(obj) {
    if (!obj || !obj.values) return { count: 0 };
    return obj.values;
}

export function handleSummary(data) {
    var m = data.metrics;
    var success = sg(m.pay_success).count || 0;
    var fail = sg(m.pay_fail).count || 0;
    var timeout = sg(m.pay_timeout).count || 0;
    var total = success + fail + timeout;

    console.log('\n━━━ Polling 복구 검증 — Phase 1 결과 ━━━');
    console.log(`총 요청: ${total}건`);
    console.log(`성공 (API 200): ${success}건`);
    console.log(`실패: ${fail}건`);
    console.log(`타임아웃 (REQUESTED 유지): ${timeout}건`);
    console.log('');
    console.log('▶ Phase 2: 2분 대기 후 DB 검증 실행하세요');
    console.log('  echo "2분 대기..." && sleep 120');
    console.log('  mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers -e "');
    console.log('    SELECT status, COUNT(*) FROM payment GROUP BY status;');
    console.log('    SELECT (SELECT COUNT(*) FROM payment WHERE status=\'SUCCESS\') as pay_success,');
    console.log('           (SELECT COUNT(*) FROM orders WHERE status=\'PAID\') as order_paid;');
    console.log('    SELECT SUM(reserved) as total_reserved FROM product_stocks;');
    console.log('    SELECT COUNT(*) as orphan FROM payment WHERE transaction_key IS NULL AND status=\'REQUESTED\';"');
    console.log('');
    console.log('▶ 검증 기준:');
    console.log('  1. REQUESTED = 0건 (전량 해소)');
    console.log('  2. Payment SUCCESS = Order PAID (정합성)');
    console.log('  3. reserved = 0 (재고 해제)');
    console.log('  4. 고아 Payment = 0건');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    return {};
}
