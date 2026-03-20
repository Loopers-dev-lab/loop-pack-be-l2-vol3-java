import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend, Rate } from 'k6/metrics';
import { authHeaders } from '../lib/helpers.js';

/**
 * CircuitBreaker 전이 사이클 검증 테스트
 *
 * CB 설정 (application.yml):
 *   sliding-window-size: 50
 *   minimum-number-of-calls: 10
 *   failure-rate-threshold: 80%
 *   wait-duration-in-open-state: 15s
 *   permitted-number-of-calls-in-half-open-state: 5
 *
 * 3-Phase로 CB CLOSED → OPEN → HALF_OPEN → CLOSED 전이를 관찰한다.
 *
 * ┌──────────────────────────────────────────────────────────────────┐
 * │  Phase A (30s): PG 정상 → 결제 성공/실패 (CB CLOSED)            │
 * │  ── docker stop pg-simulator ──                                  │
 * │  Phase B (45s): PG 다운 → CB OPEN (503 빠른 실패) 관찰           │
 * │  ── docker start pg-simulator ──                                 │
 * │  Phase C (30s): PG 복구 → CB HALF_OPEN → CLOSED 복귀 관찰       │
 * └──────────────────────────────────────────────────────────────────┘
 *
 * 사전 준비:
 *   1. seed.sh, create-k6-users.sh 실행 완료
 *   2. PG 시뮬레이터 docker 컨테이너 이름 확인 (기본: pg-simulator)
 *
 * 실행 (3개 터미널 사용):
 *
 *   # 터미널 1: k6 실행
 *   ~/k6 run k6/scripts/payment-cb-lifecycle.js
 *
 *   # 터미널 2: Phase B 시작 시 PG 중단 (k6 시작 후 30초)
 *   echo "30초 후 PG 중단..." && sleep 30 && docker stop pg-simulator && echo "PG 중단됨!"
 *
 *   # 터미널 3: Phase C 시작 시 PG 복구 (k6 시작 후 75초)
 *   echo "75초 후 PG 복구..." && sleep 75 && docker start pg-simulator && echo "PG 복구됨!"
 *
 * 검증 기준:
 *   1. Phase A: 503 응답 = 0건 (CB CLOSED 유지)
 *   2. Phase B: 503 응답 급증, p95 < 100ms (빠른 실패 — PG 호출 없이 즉시 반환)
 *   3. Phase C: 503 감소 → 200 복귀 (CB HALF_OPEN → CLOSED 전이)
 */
export const options = {
    scenarios: {
        // Phase A: PG 정상 — CB CLOSED 기준선
        phaseA: {
            executor: 'constant-vus',
            vus: 10,
            duration: '30s',
            exec: 'paymentFlow',
            env: { PHASE: 'A' },
        },
        // Phase B: PG 다운 — CB OPEN 전이 관찰
        phaseB: {
            executor: 'constant-vus',
            vus: 10,
            duration: '45s',
            startTime: '30s',
            exec: 'paymentFlow',
            env: { PHASE: 'B' },
        },
        // Phase C: PG 복구 — CB HALF_OPEN → CLOSED 복귀
        phaseC: {
            executor: 'constant-vus',
            vus: 10,
            duration: '30s',
            startTime: '75s',
            exec: 'paymentFlow',
            env: { PHASE: 'C' },
        },
    },
};

const BASE = 'http://localhost:8080';

// Phase별 메트릭
const phaseASuccess = new Counter('phaseA_success');
const phaseAFail = new Counter('phaseA_fail');
const phaseA503 = new Counter('phaseA_503');
const phaseADuration = new Trend('phaseA_duration');

const phaseBSuccess = new Counter('phaseB_success');
const phaseBFail = new Counter('phaseB_fail');
const phaseB503 = new Counter('phaseB_503');
const phaseBDuration = new Trend('phaseB_duration');

const phaseCSuccess = new Counter('phaseC_success');
const phaseCFail = new Counter('phaseC_fail');
const phaseC503 = new Counter('phaseC_503');
const phaseCDuration = new Trend('phaseC_duration');

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
        addFailMetric(__ENV.PHASE);
        sleep(0.5);
        return;
    }

    let orderId;
    try {
        orderId = JSON.parse(orderRes.body).data.orderId;
    } catch (e) {
        addFailMetric(__ENV.PHASE);
        sleep(0.5);
        return;
    }

    // 2. 결제 요청 — CB 상태에 따라 응답이 달라진다
    const paymentPayload = JSON.stringify({
        orderId: orderId,
        cardType: ['SAMSUNG', 'KB', 'HYUNDAI'][Math.floor(Math.random() * 3)],
        cardNo: '1234-5678-9012-3456',
    });

    const start = Date.now();
    const paymentRes = http.post(`${BASE}/api/v1/payments`, paymentPayload, { headers: HEADERS });
    const elapsed = Date.now() - start;

    classifyResponse(__ENV.PHASE, paymentRes, elapsed);

    sleep(0.3 + Math.random() * 0.5);
}

function classifyResponse(phase, res, elapsed) {
    const body = safeParseBody(res);
    const is503 = res.status === 503 ||
        (body && body.meta && body.meta.errorCode === 'PAYMENT_SERVICE_UNAVAILABLE');

    if (phase === 'A') {
        phaseADuration.add(elapsed);
        if (res.status === 200) phaseASuccess.add(1);
        else if (is503) phaseA503.add(1);
        else phaseAFail.add(1);
    } else if (phase === 'B') {
        phaseBDuration.add(elapsed);
        if (res.status === 200) phaseBSuccess.add(1);
        else if (is503) phaseB503.add(1);
        else phaseBFail.add(1);
    } else {
        phaseCDuration.add(elapsed);
        if (res.status === 200) phaseCSuccess.add(1);
        else if (is503) phaseC503.add(1);
        else phaseCFail.add(1);
    }
}

function addFailMetric(phase) {
    if (phase === 'A') phaseAFail.add(1);
    else if (phase === 'B') phaseBFail.add(1);
    else phaseCFail.add(1);
}

function safeParseBody(res) {
    try { return JSON.parse(res.body); } catch (e) { return null; }
}

export function handleSummary(data) {
    const m = data.metrics;

    const aSuccess = m.phaseA_success?.values?.count || 0;
    const aFail = m.phaseA_fail?.values?.count || 0;
    const a503 = m.phaseA_503?.values?.count || 0;
    const aP95 = m.phaseA_duration?.values?.['p(95)']?.toFixed(0) || '-';

    const bSuccess = m.phaseB_success?.values?.count || 0;
    const bFail = m.phaseB_fail?.values?.count || 0;
    const b503 = m.phaseB_503?.values?.count || 0;
    const bP95 = m.phaseB_duration?.values?.['p(95)']?.toFixed(0) || '-';

    const cSuccess = m.phaseC_success?.values?.count || 0;
    const cFail = m.phaseC_fail?.values?.count || 0;
    const c503 = m.phaseC_503?.values?.count || 0;
    const cP95 = m.phaseC_duration?.values?.['p(95)']?.toFixed(0) || '-';

    console.log('\n━━━ CircuitBreaker 전이 사이클 결과 ━━━');
    console.log('');
    console.log('[Phase A] PG 정상 — CB CLOSED (0~30초)');
    console.log(`  성공: ${aSuccess}건 | 실패: ${aFail}건 | 503: ${a503}건 | p95: ${aP95}ms`);
    console.log(`  검증: 503 = 0 → ${a503 === 0 ? 'PASS' : 'FAIL (CB가 정상에서 열림!)'}`);
    console.log('');
    console.log('[Phase B] PG 다운 — CB OPEN (30~75초)');
    console.log(`  성공: ${bSuccess}건 | 실패: ${bFail}건 | 503: ${b503}건 | p95: ${bP95}ms`);
    console.log(`  검증: 503 > 0 → ${b503 > 0 ? 'PASS' : 'FAIL (CB가 열리지 않음!)'}`);
    console.log(`  검증: p95 < 100ms (빠른 실패) → ${parseInt(bP95) < 100 ? 'PASS' : `${bP95}ms — PG 호출 차단 확인 필요`}`);
    console.log('');
    console.log('[Phase C] PG 복구 — CB HALF_OPEN → CLOSED (75~105초)');
    console.log(`  성공: ${cSuccess}건 | 실패: ${cFail}건 | 503: ${c503}건 | p95: ${cP95}ms`);
    console.log(`  검증: 성공 > 0 → ${cSuccess > 0 ? 'PASS' : 'FAIL (CB가 닫히지 않음!)'}`);
    console.log(`  검증: 503 < Phase B → ${c503 < b503 ? 'PASS' : 'FAIL (CB 복구 실패!)'}`);
    console.log('');
    console.log('CB 전이: CLOSED(A) → OPEN(B, 503 급증) → HALF_OPEN(C, 5건 프로브) → CLOSED(C, 성공 복귀)');
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

    return {};
}
