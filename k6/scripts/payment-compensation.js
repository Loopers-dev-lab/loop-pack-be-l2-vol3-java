import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders, adminHeaders } from '../lib/helpers.js';

/**
 * 보상 트랜잭션(Compensation) 발동 검증 테스트
 *
 * 결제 성공(PG SUCCESS 콜백) 후 stock commit 실패 시
 * PaymentCompensationService가 REQUIRES_NEW TX로 보상 기록을 생성하는지 검증한다.
 *
 * 보상 발동 조건:
 *   handleCallback → SUCCESS → stockService.commit() 예외 발생
 *   → compensationService.recordFailedCommit() (REQUIRES_NEW)
 *   → 현재 TX 롤백 → Payment CAS도 롤백 (REQUESTED 유지)
 *
 * 전략: 재고 경합 + 동시 재고 감소로 commit 실패를 유도한다.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  Step 0: 재고 2개짜리 테스트 상품 생성                            │
 * │  Step 1: 10 VU가 동시에 주문+결제 (재고 2개 상품)                 │
 * │          → hold 성공은 최대 2건, 나머지는 STOCK_NOT_ENOUGH        │
 * │  Step 2: 결제 진행 중(PG 처리 1~5초), Admin API로 재고를 0으로    │
 * │          → PG SUCCESS 콜백 도착 시 commit 실패 가능               │
 * │  Step 3: 2분 대기 (Polling + 보상 처리)                          │
 * │  Step 4: DB에서 compensation 테이블 확인                          │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * ⚠️ 보상 발동은 타이밍에 의존하므로 100% 재현이 어렵다.
 *    여러 번 반복 실행하거나, PG 시뮬레이터의 처리 지연(1~5초)을
 *    활용하여 Step 2 타이밍을 조정한다.
 *
 * 실행:
 *
 *   # Step 0: 재고 2개 테스트 상품 생성 (반환된 productId 기록)
 *   curl -s -X POST http://localhost:8080/api-admin/v1/products \
 *     -H "Content-Type: application/json" \
 *     -H "X-Loopers-Ldap: loopers.admin" \
 *     -d '{"productName":"보상테스트상품","brandId":1,"price":10000,"description":"재고2개","initialStock":2}' \
 *     | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'productId: {d[\"data\"][\"productId\"]}')"
 *
 *   # Step 1+2: k6 실행 (LOW_STOCK_PID에 위에서 받은 productId 설정)
 *   LOW_STOCK_PID=<productId> ~/k6 run k6/scripts/payment-compensation.js
 *
 *   # Step 2 병행: k6 시작 후 2초 뒤 재고를 0으로 변경
 *   #   (PG가 아직 처리 중인 동안 on_hand를 강제로 0으로 설정)
 *   echo "2초 후 재고 강제 변경..." && sleep 2 && \
 *   mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers -e "
 *     UPDATE product_stocks
 *       SET on_hand = 0
 *       WHERE product_id = (
 *         SELECT product_id FROM products WHERE product_name = '보상테스트상품' LIMIT 1
 *       );
 *     SELECT '재고 강제 변경 완료' as info;" && \
 *   echo "재고 0으로 변경됨!"
 *
 *   # Step 3: 2분 대기
 *   echo "2분 대기 (Polling + 보상 처리)..." && sleep 120
 *
 *   # Step 4: DB 검증
 *   mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers -e "
 *     SELECT '=== payment_compensation 테이블 ===' as info;
 *     SELECT * FROM payment_compensation ORDER BY created_at DESC;
 *
 *     SELECT '=== Compensation 상태 분포 ===' as info;
 *     SELECT status, COUNT(*) as cnt FROM payment_compensation GROUP BY status;
 *
 *     SELECT '=== Payment 상태 분포 ===' as info;
 *     SELECT status, COUNT(*) as cnt FROM payment
 *       WHERE order_id IN (
 *         SELECT order_id FROM order_items
 *         WHERE product_id = (
 *           SELECT product_id FROM products WHERE product_name = '보상테스트상품' LIMIT 1
 *         )
 *       )
 *       GROUP BY status;
 *
 *     SELECT '=== Order 상태 분포 ===' as info;
 *     SELECT status, COUNT(*) as cnt FROM orders
 *       WHERE order_id IN (
 *         SELECT DISTINCT order_id FROM order_items
 *         WHERE product_id = (
 *           SELECT product_id FROM products WHERE product_name = '보상테스트상품' LIMIT 1
 *         )
 *       )
 *       GROUP BY status;"
 *
 * 검증 기준:
 *   1. payment_compensation 테이블에 1건 이상 존재
 *   2. Compensation status = PENDING 또는 RESOLVED
 *   3. 해당 Payment는 REQUESTED 유지 (TX 롤백으로 CAS 원복)
 *   4. on_hand 강제 변경으로 인한 commit 실패 로그 확인:
 *      "Stock commit 실패 — 보상 기록 생성"
 */

const LOW_STOCK_PRODUCT_ID = __ENV.LOW_STOCK_PID || 101;
const BASE = 'http://localhost:8080';

export const options = {
    scenarios: {
        // 재고 2개 상품에 10 VU 동시 결제
        racePayment: {
            executor: 'shared-iterations',
            vus: 10,
            iterations: 10,
            maxDuration: '30s',
            exec: 'racePaymentFlow',
        },
    },
};

const paySuccess = new Counter('pay_success');
const payStockFail = new Counter('pay_stock_not_enough');
const payOtherFail = new Counter('pay_other_fail');
const payDuration = new Trend('pay_duration');

export function racePaymentFlow() {
    const vuId = (__VU % 50) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');

    // 1. 주문 생성 (재고 2개 상품)
    const orderPayload = JSON.stringify({
        orderType: 'DIRECT',
        items: [{ productId: parseInt(LOW_STOCK_PRODUCT_ID), quantity: 1 }],
    });

    const orderRes = http.post(`${BASE}/api/v1/orders`, orderPayload, { headers: HEADERS });

    if (orderRes.status !== 200 && orderRes.status !== 201) {
        const body = safeParseBody(orderRes);
        if (body?.meta?.errorCode === 'STOCK_NOT_ENOUGH') {
            payStockFail.add(1);
        } else {
            payOtherFail.add(1);
        }
        return;
    }

    const orderId = safeGetOrderId(orderRes);
    if (!orderId) { payOtherFail.add(1); return; }

    // 2. 결제 요청 (hold 시점에서 CAS 경합)
    const paymentPayload = JSON.stringify({
        orderId: orderId,
        cardType: 'SAMSUNG',
        cardNo: '1234-5678-9012-3456',
    });

    const start = Date.now();
    const paymentRes = http.post(`${BASE}/api/v1/payments`, paymentPayload, { headers: HEADERS });
    payDuration.add(Date.now() - start);

    if (paymentRes.status === 200) {
        paySuccess.add(1);
    } else {
        const body = safeParseBody(paymentRes);
        if (body?.meta?.errorCode === 'STOCK_NOT_ENOUGH') {
            payStockFail.add(1);
        } else {
            payOtherFail.add(1);
        }
    }
}

function safeParseBody(res) {
    try { return JSON.parse(res.body); } catch (e) { return null; }
}

function safeGetOrderId(res) {
    try { return JSON.parse(res.body).data.orderId; } catch (e) { return null; }
}

export function handleSummary(data) {
    const m = data.metrics;
    const success = m.pay_success?.values?.count || 0;
    const stockFail = m.pay_stock_not_enough?.values?.count || 0;
    const otherFail = m.pay_other_fail?.values?.count || 0;

    console.log('\n━━━ 보상 트랜잭션 발동 검증 — Step 1 결과 ━━━');
    console.log(`결제 성공 (PG 요청됨): ${success}건  ← 이 중 commit 실패 시 보상 발동`);
    console.log(`STOCK_NOT_ENOUGH:      ${stockFail}건`);
    console.log(`기타 실패:             ${otherFail}건`);
    console.log('');
    console.log(`오버셀 검증: 성공 ≤ 2 → ${success <= 2 ? 'PASS' : 'FAIL (오버셀!)'}`);
    console.log('');
    console.log('▶ Step 2: k6와 병행하여 on_hand를 0으로 변경했는지 확인');
    console.log('  (PG가 SUCCESS 콜백을 보내는 시점에 on_hand=0 → commit 실패 → 보상)');
    console.log('');
    console.log('▶ Step 3: 2분 대기 후 DB 검증');
    console.log('  mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers');
    console.log('');
    console.log('  검증 항목:');
    console.log('  1. payment_compensation 테이블에 1건 이상 존재');
    console.log('  2. Compensation status = PENDING 또는 RESOLVED');
    console.log('  3. 해당 Payment = REQUESTED 유지 (TX 롤백)');
    console.log('  4. 서버 로그: "Stock commit 실패 — 보상 기록 생성"');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    return {};
}
