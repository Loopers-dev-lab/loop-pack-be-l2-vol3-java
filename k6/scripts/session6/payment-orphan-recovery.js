import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * Phase C 고아 Payment 강제 생성 + Polling 복구 검증
 *
 * 고아 Payment = REQUESTED 상태 + transactionKey=null
 * 발생 조건: TX-1(Payment INSERT) 커밋 후, PG 호출 전에 타임아웃/서버 다운
 *
 * 이 테스트는 PG 시뮬레이터를 의도적으로 중단하여 고아를 강제 생성하고,
 * PG 복구 후 Polling 스케줄러(60초)가 orderId 기반으로 복구하는지 검증한다.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  Phase 1 (10s): PG 정상 운영 — 기준선 수집                       │
 * │  ── docker stop pg-simulator ──                                  │
 * │  Phase 2 (15s): PG 다운 — 고아 Payment 강제 생성                 │
 * │  ── docker start pg-simulator ──                                 │
 * │  Phase 3 (2분): Polling 스케줄러가 고아를 복구할 시간 확보         │
 * │  Phase 4: DB 직접 조회로 고아 전량 해소 확인                      │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * 실행 (3개 터미널):
 *
 *   # 터미널 1: k6 실행
 *   ~/k6 run k6/scripts/payment-orphan-recovery.js
 *
 *   # 터미널 2: Phase 2 시작 시 PG 중단 (k6 시작 후 10초)
 *   echo "10초 후 PG 중단..." && sleep 10 && docker stop pg-simulator && echo "PG 중단됨!"
 *
 *   # 터미널 3: Phase 2 종료 후 PG 복구 (k6 시작 후 25초)
 *   echo "25초 후 PG 복구..." && sleep 25 && docker start pg-simulator && echo "PG 복구됨!"
 *
 * k6 종료 후 Phase 3~4 검증:
 *
 *   # Phase 3: 2분 대기 (Polling 스케줄러 2~3회 동작)
 *   echo "2분 대기 (Polling 복구 중)..." && sleep 120
 *
 *   # Phase 4: DB 검증
 *   mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers -e "
 *     SELECT '=== 고아 Payment (TK=null + REQUESTED) ===' as info;
 *     SELECT COUNT(*) as orphan_count
 *       FROM payment WHERE transaction_key IS NULL AND status = 'REQUESTED';
 *
 *     SELECT '=== REQUESTED 잔류 ===' as info;
 *     SELECT COUNT(*) as requested_count FROM payment WHERE status = 'REQUESTED';
 *
 *     SELECT '=== Payment 상태 분포 ===' as info;
 *     SELECT status, COUNT(*) as cnt FROM payment GROUP BY status;
 *
 *     SELECT '=== 정합성: SUCCESS = PAID ===' as info;
 *     SELECT (SELECT COUNT(*) FROM payment WHERE status='SUCCESS') as payment_success,
 *            (SELECT COUNT(*) FROM orders WHERE status='PAID') as order_paid;
 *
 *     SELECT '=== 재고 reserved 잔여 ===' as info;
 *     SELECT SUM(reserved) as total_reserved FROM product_stocks;
 *
 *     SELECT '=== 최근 고아 복구 로그 (transactionKey 매핑) ===' as info;
 *     SELECT payment_id, order_id, transaction_key, status, updated_at
 *       FROM payment
 *       WHERE transaction_key IS NOT NULL
 *         AND updated_at > DATE_SUB(NOW(), INTERVAL 3 MINUTE)
 *       ORDER BY updated_at DESC LIMIT 10;"
 *
 * 검증 기준:
 *   1. 고아 Payment (TK=null + REQUESTED) = 0건
 *   2. REQUESTED 잔류 = 0건
 *   3. Payment SUCCESS 수 = Order PAID 수
 *   4. reserved = 0 (모든 재고 해제)
 */
export const options = {
    scenarios: {
        // Phase 1: PG 정상 — 기준선
        normalTraffic: {
            executor: 'constant-vus',
            vus: 10,
            duration: '10s',
            exec: 'paymentFlow',
            env: { PHASE: 'normal' },
        },
        // Phase 2: PG 다운 — 고아 생성 구간
        orphanCreation: {
            executor: 'constant-vus',
            vus: 15,
            duration: '15s',
            startTime: '10s',
            exec: 'paymentFlow',
            env: { PHASE: 'orphan' },
        },
    },
};

const BASE = 'http://localhost:8080';

// 메트릭
const normalSuccess = new Counter('normal_success');
const normalFail = new Counter('normal_fail');

const orphanTimeout = new Counter('orphan_timeout');
const orphanPgError = new Counter('orphan_pg_error');
const orphanCbOpen = new Counter('orphan_cb_open');
const orphanOther = new Counter('orphan_other');
const orphanSuccess = new Counter('orphan_unexpected_success');

const paymentDuration = new Trend('payment_duration');

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
        if (__ENV.PHASE === 'normal') normalFail.add(1);
        else orphanOther.add(1);
        sleep(0.3);
        return;
    }

    let orderId;
    try {
        orderId = JSON.parse(orderRes.body).data.orderId;
    } catch (e) {
        if (__ENV.PHASE === 'normal') normalFail.add(1);
        else orphanOther.add(1);
        sleep(0.3);
        return;
    }

    // 2. 결제 요청
    const paymentPayload = JSON.stringify({
        orderId: orderId,
        cardType: ['SAMSUNG', 'KB', 'HYUNDAI'][Math.floor(Math.random() * 3)],
        cardNo: '1234-5678-9012-3456',
    });

    const start = Date.now();
    const paymentRes = http.post(`${BASE}/api/v1/payments`, paymentPayload, {
        headers: HEADERS,
        timeout: '15s',  // k6 자체 타임아웃 (서버보다 길게)
    });
    paymentDuration.add(Date.now() - start);

    if (__ENV.PHASE === 'normal') {
        if (paymentRes.status === 200) normalSuccess.add(1);
        else normalFail.add(1);
    } else {
        // Phase 2: PG 다운 상태 — 에러 타입 분류
        if (paymentRes.status === 200) {
            orphanSuccess.add(1);  // CB가 아직 안 열렸거나, PG가 아직 안 죽었음
        } else {
            const body = safeParseBody(paymentRes);
            const errorCode = body?.meta?.errorCode || '';
            if (errorCode === 'PAYMENT_PG_TIMEOUT') {
                orphanTimeout.add(1);  // ★ 타임아웃 → 고아 생성됨 (REQUESTED + TK=null)
            } else if (errorCode === 'PAYMENT_PG_ERROR') {
                orphanPgError.add(1);  // 연결 실패 → 즉시 FAILED (고아 아님)
            } else if (errorCode === 'PAYMENT_SERVICE_UNAVAILABLE') {
                orphanCbOpen.add(1);   // CB OPEN → Payment 생성 안 됨 (고아 아님)
            } else {
                orphanOther.add(1);
            }
        }
    }

    sleep(0.3 + Math.random() * 0.5);
}

function safeParseBody(res) {
    try { return JSON.parse(res.body); } catch (e) { return null; }
}

export function handleSummary(data) {
    const m = data.metrics;

    const nSuccess = m.normal_success?.values?.count || 0;
    const nFail = m.normal_fail?.values?.count || 0;

    const oTimeout = m.orphan_timeout?.values?.count || 0;
    const oPgError = m.orphan_pg_error?.values?.count || 0;
    const oCbOpen = m.orphan_cb_open?.values?.count || 0;
    const oOther = m.orphan_other?.values?.count || 0;
    const oSuccess = m.orphan_unexpected_success?.values?.count || 0;

    console.log('\n━━━ Phase C 고아 Payment 복구 검증 결과 ━━━');
    console.log('');
    console.log('[Phase 1] PG 정상 (0~10초)');
    console.log(`  성공: ${nSuccess}건 | 실패: ${nFail}건`);
    console.log('');
    console.log('[Phase 2] PG 다운 — 고아 생성 구간 (10~25초)');
    console.log(`  PAYMENT_PG_TIMEOUT: ${oTimeout}건  ← ★ 고아 생성됨 (REQUESTED + TK=null)`);
    console.log(`  PAYMENT_PG_ERROR:   ${oPgError}건  ← 즉시 FAILED (고아 아님)`);
    console.log(`  CB OPEN (503):      ${oCbOpen}건  ← Payment 미생성 (고아 아님)`);
    console.log(`  예상외 성공:        ${oSuccess}건`);
    console.log(`  기타:               ${oOther}건`);
    console.log('');
    console.log(`  ★ 예상 고아 수: ${oTimeout}건 (TIMEOUT만 고아 생성)`);
    console.log('');
    console.log('▶ Phase 3: 2분 대기 (Polling 스케줄러가 고아 복구)');
    console.log('  echo "2분 대기..." && sleep 120');
    console.log('');
    console.log('▶ Phase 4: DB 검증');
    console.log('  mysql -h 127.0.0.1 -P 3306 -u application -papplication loopers');
    console.log('');
    console.log('  검증 항목:');
    console.log('  1. 고아(TK=null+REQUESTED) = 0건 → Polling Phase C 복구 성공');
    console.log('  2. REQUESTED 잔류 = 0건 → 전량 해소');
    console.log('  3. Payment SUCCESS = Order PAID → 정합성');
    console.log('  4. reserved = 0 → 재고 완전 해제');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    return {};
}
