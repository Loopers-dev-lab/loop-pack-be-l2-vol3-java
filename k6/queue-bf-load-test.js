import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';
import exec from 'k6/execution';

// =============================================================================
// 블랙 프라이데이 시나리오 부하 테스트 (Open-loop)
//
// 목적:
//   시스템 수용치(80 TPS)를 초과하는 요청이 들어올 때,
//   대기열이 요청을 줄세우고 순서를 보장하여 처리하는지 검증한다.
//
// 핵심 검증:
//   1. 수용치 초과 요청은 QUEUED (드롭 없음)
//   2. QUEUED된 유저는 순서대로 ADMITTED
//   3. 대기열이 가득 차면 QUEUE_FULL 반환 (48,000명 초과 시)
//   4. 대기열 크기와 무관하게 DB 커넥션 풀은 안전 (ρ ≤ 0.7 목표)
//
// Open-loop 설계:
//   ramping-arrival-rate — iteration 완료 여부와 무관하게 초당 N건 투입.
//   VU가 대기열 폴링에 묶여도 새로운 요청이 계속 들어온다.
//   이전 버전(ramping-vus, closed-loop)에서는 VU가 폴링에 묶여
//   실제 도착률이 설계값보다 크게 낮았음 (1000 VU → 실측 40 enter/s).
//
// 급간 설계 (초당 iteration 수 = 초당 새 queue/enter 호출 수):
//   T1 (정상):      30/s,  30s → 입장 80/s > 도착, 대기열 비어있음
//   T2 (임계):      80/s,  60s → 도착 = 입장, 균형점
//   T3 (초과):     150/s,  60s → 순 70/s 누적, 대기열 증가
//   T4 (블프 피크): 200/s,  90s → 순 120/s 누적, 대기열 급증
//   T5 (쿨다운):     0/s, 120s → 대기열 소진, 시스템 복귀
//
// 대기열 성장 예측:
//   T3: 70/s 순누적 × 60s = 4,200
//   T4: 120/s 순누적 × 90s = 10,800  →  합산 ~15,000
//   max_queue=1,000 설정 시 T3 시작 14초 만에 QUEUE_FULL 도달
//
// 5차 교훈 (Open-loop + VU 기반 userId):
//   - VU = userId 고정이면 maxVUs = 고유 유저 수 상한
//   - 5,000 VU로는 48,000 대기열 불가능 (동일 유저 재진입 = 대기열 +0)
//   - iterationInTest 기반 userId 매핑으로 매 iteration 고유 유저 배정
//   - max_queue 축소로 QUEUE_FULL 메커니즘 검증 (표준 부하 테스트 접근)
//
// 사전 준비:
//   1. ./scripts/seed-test-data.sh         (상품/브랜드 생성)
//   2. ./scripts/seed-bf-test-data.sh 5000 (유저 5000명 생성)
//   3. ./scripts/reset-bf-test.sh          (매 테스트 전 초기화)
//   4. 서버: queue.max-size=1000 설정 후 재시작 (QUEUE_FULL 검증 시)
//
// 실행:
//   k6 run k6/queue-bf-load-test.js
//   k6 run -e MAX_USERS=5000 k6/queue-bf-load-test.js
//
// Grafana 모니터링:
//   http://localhost:3000 → Queue System 대시보드
// =============================================================================

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ACTUATOR_URL = __ENV.ACTUATOR_URL || 'http://localhost:8081';
const MAX_USERS = parseInt(__ENV.MAX_USERS || '5000');

// --- Custom Metrics ---

// 주문
const orderDuration = new Trend('order_duration', true);
const orderSuccess = new Counter('order_success');
const orderFailed = new Counter('order_failed');

// 대기열
const queueWaitTime = new Trend('queue_wait_time', true);
const queueEnterQueued = new Counter('queue_enter_queued');
const queueEnterAdmitted = new Counter('queue_enter_admitted');
const queueEnterFull = new Counter('queue_enter_full');
const queueEnterError = new Counter('queue_enter_error');

// 비율
const orderFailRate = new Rate('order_fail_rate');
const queueFullRate = new Rate('queue_full_rate');

// --- 시나리오 급간 ---
export const options = {
    scenarios: {
        // 블프 트래픽: Open-loop 5급간
        bf_traffic: {
            executor: 'ramping-arrival-rate',
            startRate: 0,
            timeUnit: '1s',
            stages: [
                // T1: 정상 (30/s, 30s)
                { duration: '10s', target: 30 },
                { duration: '20s', target: 30 },

                // T2: 임계 (80/s, 60s) — 입장률과 동일
                { duration: '10s', target: 80 },
                { duration: '50s', target: 80 },

                // T3: 초과 (150/s, 60s)
                { duration: '10s', target: 150 },
                { duration: '50s', target: 150 },

                // T4: 블프 피크 (200/s, 90s)
                { duration: '15s', target: 200 },
                { duration: '75s', target: 200 },

                // T5: 쿨다운 (0/s)
                { duration: '10s', target: 0 },
            ],
            preAllocatedVUs: 5000,
            maxVUs: 10000,
            gracefulStop: '120s', // 대기열 폴링 중인 VU 종료 대기
        },
        // HikariCP + 대기열 모니터링 (1초 주기)
        monitor: {
            executor: 'constant-arrival-rate',
            rate: 1,
            timeUnit: '1s',
            duration: '400s',
            preAllocatedVUs: 1,
            maxVUs: 1,
            exec: 'monitorSystem',
        },
    },
    thresholds: {
        // BF 시나리오: row lock 경합으로 p99가 높아질 수 있음
        // 시스템 생존 확인 (죽지 않고 처리 완료)
        order_duration: ['p(99)<10000'], // 10초 이내
    },
};

// --- 메인 시나리오: BF 트래픽 (Open-loop) ---
export default function () {
    // iteration 번호 → 유저 매핑 (매 iteration마다 고유 유저 배정)
    // VU 기반 매핑은 동일 VU가 같은 userId를 반복 사용하여 대기열이 쌓이지 않음
    const userId = (exec.scenario.iterationInTest % MAX_USERS) + 1;
    const paddedId = String(userId).padStart(4, '0');
    const loginId = `bf${paddedId}`;
    const authHeaders = {
        'X-Loopers-LoginId': loginId,
        'X-Loopers-LoginPw': 'Password1!',
    };

    // 대기열 진입 → 대기 → 주문 (sleep 없음 — 최대 압력)
    queueAndOrderFlow(authHeaders, userId);
}

function queueAndOrderFlow(authHeaders, userId) {
    // 1. Enter queue
    const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
        headers: authHeaders,
        tags: { name: 'queue_enter' },
    });

    if (enterRes.status !== 200) {
        queueEnterError.add(1);
        queueFullRate.add(false);
        return;
    }

    const enterData = enterRes.json('data');
    if (!enterData) {
        queueEnterError.add(1);
        queueFullRate.add(false);
        return;
    }

    const status = enterData.status;

    // QUEUE_FULL → 즉시 반환
    if (status === 'QUEUE_FULL') {
        queueEnterFull.add(1);
        queueFullRate.add(true);
        return;
    }

    // ADMITTED → 바로 주문
    if (status === 'ADMITTED') {
        queueEnterAdmitted.add(1);
        queueFullRate.add(false);
        placeOrder(authHeaders, userId);
        return;
    }

    // QUEUED → 폴링 대기 (서버 권장 주기 사용)
    queueEnterQueued.add(1);
    queueFullRate.add(false);

    // 2. Poll for admission
    const suggestedInterval = enterData.suggestedPollIntervalMs || 3000;
    const startWait = Date.now();
    let admitted = false;

    while (Date.now() - startWait < 120000) {
        sleep(suggestedInterval / 1000); // 폴링 간격 (서버 권장)

        const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
            headers: authHeaders,
            tags: { name: 'queue_position' },
        });

        if (posRes.status !== 200) continue;

        const posData = posRes.json('data');
        if (!posData) continue;

        if (posData.status === 'ADMITTED') {
            admitted = true;
            break;
        }

        if (posData.status === 'NOT_IN_QUEUE') {
            break;
        }
    }

    queueWaitTime.add(Date.now() - startWait);

    if (!admitted) {
        orderFailed.add(1);
        orderFailRate.add(true);
        return;
    }

    // 3. Place order
    placeOrder(authHeaders, userId);
}

function placeOrder(authHeaders, userId) {
    const productId = (userId % 5) + 1;
    const orderPayload = JSON.stringify({
        items: [{ productId: productId, quantity: 1 }],
    });

    const start = Date.now();
    const orderRes = http.post(`${BASE_URL}/api/v1/orders`, orderPayload, {
        headers: Object.assign({}, authHeaders, { 'Content-Type': 'application/json' }),
        tags: { name: 'order_create' },
    });
    orderDuration.add(Date.now() - start);

    const success = check(orderRes, {
        'order created (201)': (r) => r.status === 201,
    });

    if (success) {
        orderSuccess.add(1);
        orderFailRate.add(false);
    } else {
        orderFailed.add(1);
        orderFailRate.add(true);
    }
}

// --- 시스템 모니터링 시나리오 ---
export function monitorSystem() {
    const res = http.get(`${ACTUATOR_URL}/actuator/prometheus`, {
        tags: { name: 'actuator' },
    });

    if (res.status !== 200) return;

    const body = res.body;

    // HikariCP
    const activeMatch = body.match(/hikaricp_connections_active\{[^}]*\}\s+([\d.]+)/);
    const pendingMatch = body.match(/hikaricp_connections_pending\{[^}]*\}\s+([\d.]+)/);
    const totalMatch = body.match(/hikaricp_connections\{[^}]*pool="mysql-main-pool"[^}]*\}\s+([\d.]+)/);

    const active = activeMatch ? parseFloat(activeMatch[1]) : 0;
    const pending = pendingMatch ? parseFloat(pendingMatch[1]) : 0;
    const total = totalMatch ? parseFloat(totalMatch[1]) : 40;

    // 대기열 커스텀 메트릭
    const queueSizeMatch = body.match(/queue_waiting_size\{[^}]*\}\s+([\d.]+)/);
    const admissionCountMatch = body.match(/queue_admission_count_total\{[^}]*\}\s+([\d.]+)/);
    const queueFullMatch = body.match(/queue_enter_status_total\{[^}]*status="QUEUE_FULL"[^}]*\}\s+([\d.]+)/);

    const queueSize = queueSizeMatch ? parseInt(queueSizeMatch[1]) : 0;
    const admissionTotal = admissionCountMatch ? parseFloat(admissionCountMatch[1]) : 0;
    const queueFullTotal = queueFullMatch ? parseFloat(queueFullMatch[1]) : 0;

    const rho = total > 0 ? (active / total).toFixed(3) : '?';

    console.log(
        `[Monitor] ρ=${rho} active=${active}/${total} pending=${pending} | ` +
        `queue=${queueSize} admitted_total=${admissionTotal} queue_full_total=${queueFullTotal}`
    );
}
