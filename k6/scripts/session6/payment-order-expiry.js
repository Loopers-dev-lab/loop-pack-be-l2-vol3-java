import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * 주문 만료 배치 검증 테스트
 *
 * 주문 생성 후 결제를 의도적으로 하지 않거나, 결제를 실패시켜
 * OrderExpiryScheduler(60초 간격)가 만료 처리하는지 검증한다.
 *
 * 주문 만료 조건: status=PENDING_PAYMENT AND expiresAt < NOW()
 * 만료 시 처리: reserved release + 쿠폰 복원 + 장바구니 복원(DIRECT만)
 *
 * ⚠️ 주문 만료 시간(expiresAt)은 주문 생성 시 now()+15분으로 설정된다.
 *    실제 15분 대기는 비효율적이므로, 이 테스트는 2가지 전략을 사용한다:
 *
 *    전략 A: 결제 없이 주문만 생성 → DB에서 expiresAt을 과거로 수동 변경
 *    전략 B: 결제 실패 주문 생성 → 활성 결제 없는 PENDING_PAYMENT 상태 유지
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  Step 1: 주문 30건 생성 (결제 안 함 — PENDING_PAYMENT 유지)      │
 * │  Step 2: DB에서 expiresAt을 과거로 변경 (만료 조건 충족)         │
 * │  Step 3: 2분 대기 (OrderExpiryScheduler 2~3회 동작)             │
 * │  Step 4: DB 직접 조회로 만료 처리 확인                           │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * 실행:
 *
 *   # Step 0: DB 초기화 (이전 테스트 잔여 데이터 정리)
 *   mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers -e "
 *     DELETE FROM payment_compensation;
 *     DELETE FROM payment;
 *     DELETE FROM order_cart_restore;
 *     DELETE FROM order_items;
 *     DELETE FROM orders;
 *     UPDATE product_stocks SET reserved = 0;"
 *
 *   # Step 1: 주문 생성 (결제 안 함)
 *   ~/k6 run k6/scripts/payment-order-expiry.js
 *
 *   # Step 2: expiresAt을 과거로 변경 → 만료 조건 즉시 충족
 *   mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers -e "
 *     SELECT '=== 만료 대상 주문 수 (변경 전) ===' as info;
 *     SELECT COUNT(*) as pending_orders
 *       FROM orders WHERE status = 'PENDING_PAYMENT';
 *
 *     UPDATE orders
 *       SET expires_at = DATE_SUB(NOW(), INTERVAL 1 MINUTE)
 *       WHERE status = 'PENDING_PAYMENT';
 *
 *     SELECT '=== expiresAt 변경 완료 ===' as info;
 *     SELECT COUNT(*) as expired_candidates
 *       FROM orders
 *       WHERE status = 'PENDING_PAYMENT'
 *         AND expires_at < NOW();"
 *
 *   # Step 3: 2분 대기 (만료 배치 동작)
 *   echo "2분 대기 (OrderExpiryScheduler 동작 중)..." && sleep 120
 *
 *   # Step 4: 만료 처리 확인
 *   mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers -e "
 *     SELECT '=== Order 상태 분포 ===' as info;
 *     SELECT status, COUNT(*) as cnt FROM orders GROUP BY status;
 *
 *     SELECT '=== PENDING_PAYMENT 잔류 (0이어야 함) ===' as info;
 *     SELECT COUNT(*) as still_pending
 *       FROM orders WHERE status = 'PENDING_PAYMENT';
 *
 *     SELECT '=== EXPIRED 주문 수 ===' as info;
 *     SELECT COUNT(*) as expired_orders
 *       FROM orders WHERE status = 'EXPIRED';
 *
 *     SELECT '=== 재고 reserved 잔여 (0이어야 함) ===' as info;
 *     SELECT SUM(reserved) as total_reserved FROM product_stocks;
 *
 *     SELECT '=== 장바구니 복원 기록 ===' as info;
 *     SELECT COUNT(*) as restore_count FROM order_cart_restore;
 *
 *     SELECT '=== 쿠폰 복원 확인 (USED 상태 = 0이어야 함) ===' as info;
 *     SELECT status, COUNT(*) as cnt
 *       FROM user_coupons
 *       WHERE order_id IN (SELECT order_id FROM orders WHERE status = 'EXPIRED')
 *       GROUP BY status;"
 *
 * 검증 기준:
 *   1. PENDING_PAYMENT 잔류 = 0건 (전량 만료 처리)
 *   2. EXPIRED 수 = Step 1에서 생성한 주문 수
 *   3. reserved = 0 (재고 완전 해제)
 *   4. 장바구니 복원 기록 존재 (DIRECT 주문의 경우)
 */
export const options = {
    scenarios: {
        // 주문만 생성하고 결제는 하지 않는다
        createOrders: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 30,
            maxDuration: '1m',
            exec: 'createOrderOnly',
        },
    },
};

const BASE = 'http://localhost:8080';

const orderCreated = new Counter('order_created');
const orderFailed = new Counter('order_failed');

export function createOrderOnly() {
    const vuId = (__VU % 50) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');

    // 주문 생성 (DIRECT) — 결제는 하지 않음
    const orderPayload = JSON.stringify({
        orderType: 'DIRECT',
        items: [{
            productId: Math.floor(Math.random() * 100) + 1,
            quantity: 1,
        }],
    });

    const orderRes = http.post(`${BASE}/api/v1/orders`, orderPayload, { headers: HEADERS });

    if (orderRes.status === 200 || orderRes.status === 201) {
        orderCreated.add(1);
        let orderId;
        try {
            orderId = JSON.parse(orderRes.body).data.orderId;
        } catch (e) {
            // orderId 파싱 실패해도 주문은 생성됨
        }
    } else {
        orderFailed.add(1);
        const body = safeParseBody(orderRes);
        if (body && body.meta) {
            // PENDING_LIMIT_EXCEEDED 발생 시 짧게 대기
            if (body.meta.errorCode === 'ORDER_PENDING_LIMIT_EXCEEDED') {
                sleep(1);
            }
        }
    }

    sleep(0.1);
}

function safeParseBody(res) {
    try { return JSON.parse(res.body); } catch (e) { return null; }
}

export function handleSummary(data) {
    const m = data.metrics;
    const created = m.order_created?.values?.count || 0;
    const failed = m.order_failed?.values?.count || 0;

    console.log('\n━━━ 주문 만료 배치 검증 — Step 1 결과 ━━━');
    console.log(`주문 생성 성공: ${created}건`);
    console.log(`주문 생성 실패: ${failed}건 (PENDING_LIMIT 등)`);
    console.log('');
    console.log('▶ Step 2: expiresAt을 과거로 변경');
    console.log('  mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers -e "');
    console.log('    UPDATE orders SET expires_at = DATE_SUB(NOW(), INTERVAL 1 MINUTE)');
    console.log('    WHERE status = \'PENDING_PAYMENT\';"');
    console.log('');
    console.log('▶ Step 3: 2분 대기');
    console.log('  echo "2분 대기..." && sleep 120');
    console.log('');
    console.log('▶ Step 4: DB 검증');
    console.log('  검증 항목:');
    console.log(`  1. PENDING_PAYMENT 잔류 = 0 (${created}건 전량 만료)`);
    console.log(`  2. EXPIRED = ${created}건`);
    console.log('  3. reserved = 0 (재고 완전 해제)');
    console.log('  4. 장바구니 복원 기록 존재');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    return {};
}
