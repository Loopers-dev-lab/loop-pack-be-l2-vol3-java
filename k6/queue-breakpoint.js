/**
 * 대기열 시스템 한계점 탐색 (Queue Breakpoint Test)
 *
 * 목적:
 *   1. 대기열 진입/폴링 (Redis only) vs 주문 API (DB) 구간별 한계 확인
 *   2. BATCH_SIZE=80이 HikariCP(max 40) 기준으로 안전한 숫자인지 검증
 *
 * 실행 방법:
 *   # Phase 1만: 대기열 진입/폴링 (Redis 부하)
 *   k6 run -e PHASE=1 k6/queue-breakpoint.js
 *
 *   # Phase 2만: 토큰 발급 후 주문 (DB 부하 — BATCH_SIZE 검증)
 *   k6 run -e PHASE=2 -e PRODUCT_ID=1 k6/queue-breakpoint.js
 *
 *   # 전체 흐름 (enter → poll → 토큰 확인 → 주문)
 *   k6 run -e PHASE=full -e PRODUCT_ID=1 k6/queue-breakpoint.js
 *
 * Grafana 연동:
 *   k6 run -e PHASE=full -e PRODUCT_ID=1 --out influxdb=http://localhost:8086/k6 k6/queue-breakpoint.js
 *
 * 준비:
 *   - seed-black-friday.http 실행 (상품/유저 데이터 세팅)
 *   - docker-compose -f docker/infra-compose.yml up (MySQL + Redis)
 *   - ./gradlew :apps:commerce-api:bootRun
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate, Counter } from 'k6/metrics';

const BASE_URL    = __ENV.BASE_URL   || 'http://localhost:8080';
const PRODUCT_ID  = __ENV.PRODUCT_ID || '1';
const PHASE       = __ENV.PHASE      || 'full'; // '1' | '2' | 'full'
const PASSWORD    = 'Bptest123!';
const USER_PREFIX = 'q_user_';
const TOTAL_USERS = 300;

// ── 커스텀 메트릭 ──────────────────────────────────────────────
const enterDuration  = new Trend('queue_enter_duration', true);   // 대기열 진입 응답시간
const pollDuration   = new Trend('queue_poll_duration', true);    // 순번 조회 응답시간
const orderDuration  = new Trend('order_duration', true);         // 주문 응답시간
const tokenReceived  = new Counter('token_received');             // 토큰 발급 받은 횟수
const orderSuccess   = new Counter('order_success');              // 주문 성공 횟수
const queueError     = new Rate('queue_error_rate');              // 대기열 에러율
const orderError     = new Rate('order_error_rate');              // 주문 에러율

// ── 단계 설정 ──────────────────────────────────────────────────
// VU = Virtual User (가상 사용자)
// 각 VU는 독립적으로 루프를 돌며 요청을 보냄
const STAGES = {
    '1': [   // Phase 1: Redis 부하 (대기열 진입/폴링만)
        { duration: '10s', target: 50  },
        { duration: '30s', target: 50  },
        { duration: '10s', target: 150 },
        { duration: '30s', target: 150 },
        { duration: '10s', target: 300 },
        { duration: '30s', target: 300 },
        { duration: '5s',  target: 0   },
    ],
    '2': [   // Phase 2: DB 부하 (토큰 직접 발급 후 주문)
        { duration: '5s',  target: 10  },
        { duration: '30s', target: 10  },  // 베이스라인
        { duration: '5s',  target: 30  },
        { duration: '30s', target: 30  },  // HikariCP 40 이하
        { duration: '5s',  target: 50  },
        { duration: '30s', target: 50  },  // HikariCP 40 초과 예상 → 지연 발생 구간
        { duration: '5s',  target: 80  },
        { duration: '30s', target: 80  },  // BATCH_SIZE 수준 → 어떻게 되는지
        { duration: '5s',  target: 0   },
    ],
    'full': [ // Full: 전체 흐름 (enter → poll → 주문)
        { duration: '5s',  target: 20  },
        { duration: '30s', target: 20  },
        { duration: '5s',  target: 50  },
        { duration: '30s', target: 50  },
        { duration: '5s',  target: 100 },
        { duration: '30s', target: 100 },
        { duration: '5s',  target: 0   },
    ],
};

export const options = {
    stages: STAGES[PHASE] || STAGES['full'],
    thresholds: {
        'queue_enter_duration': ['p(99)<500'],   // 대기열 진입: Redis만이므로 빠르게
        'queue_poll_duration':  ['p(99)<500'],   // 순번 조회: 동일
        'order_duration':       ['p(99)<3000'],  // 주문: DB 포함이므로 여유 있게
        'queue_error_rate':     ['rate<0.01'],
        'order_error_rate':     ['rate<0.05'],
    },
    summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ── setup: 유저 사전 등록 ──────────────────────────────────────
export function setup() {
    console.log(`[setup] Phase: ${PHASE} | 유저 ${TOTAL_USERS}명 등록 중...`);
    const headers = { 'Content-Type': 'application/json' };

    for (let i = 1; i <= TOTAL_USERS; i++) {
        const loginId = `${USER_PREFIX}${String(i).padStart(3, '0')}`;
        http.post(
            `${BASE_URL}/api/v1/users`,
            JSON.stringify({
                loginId,
                password: PASSWORD,
                name: `큐테스트${i}`,
                birthDate: '19900101',
                email: `${loginId}@qtest.com`,
            }),
            { headers }
        );
    }

    console.log('[setup] 유저 등록 완료.');
    return { productId: parseInt(PRODUCT_ID) };
}

// ── 메인 시나리오 ──────────────────────────────────────────────
export default function (data) {
    const vuIndex = ((__VU - 1) % TOTAL_USERS) + 1;
    const userId  = `${USER_PREFIX}${String(vuIndex).padStart(3, '0')}`;

    if (PHASE === '1') {
        runQueueOnly(userId);
    } else if (PHASE === '2') {
        runOrderWithFakeToken(userId, data.productId);
    } else {
        runFullFlow(userId, data.productId);
    }
}

// Phase 1: 대기열 진입 + 폴링만 (Redis 부하 측정)
// → 목적: DB 없이 Redis만으로 몇 명까지 견디는지 확인
function runQueueOnly(userId) {
    // 대기열 진입
    const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter?userId=${userId}`);
    enterDuration.add(enterRes.timings.duration);
    const enterOk = check(enterRes, { '진입 200': (r) => r.status === 200 });
    queueError.add(!enterOk);

    sleep(0.1);

    // 순번 조회
    const pollRes = http.get(`${BASE_URL}/api/v1/queue/position?userId=${userId}`);
    pollDuration.add(pollRes.timings.duration);
    const pollOk = check(pollRes, { '조회 200': (r) => r.status === 200 });
    queueError.add(!pollOk);
}

// Phase 2: 토큰을 직접 세팅 후 주문 바로 진입 (DB 병목 측정)
// → 목적: HikariCP 40개 기준으로 동시 요청이 몇 개까지 버티는지 확인
//         BATCH_SIZE=80이 40을 초과해도 괜찮은지 실측
function runOrderWithFakeToken(userId, productId) {
    // 대기열 진입 (토큰 발급 트리거)
    http.post(`${BASE_URL}/api/v1/queue/enter?userId=${userId}`);

    // 스케줄러가 토큰 발급하길 기다림 (최대 5초 주기)
    let token = null;
    for (let attempt = 0; attempt < 4; attempt++) {
        sleep(2);
        const pollRes = http.get(`${BASE_URL}/api/v1/queue/position?userId=${userId}`);
        if (pollRes.status === 200) {
            const body = pollRes.json();
            if (body.data && body.data.token) {
                token = body.data.token;
                tokenReceived.add(1);
                break;
            }
        }
    }

    if (!token) return; // 토큰 못 받으면 스킵

    // 주문 API 요청 (HikariCP 커넥션 소비)
    const orderRes = http.post(
        `${BASE_URL}/api/v1/orders`,
        JSON.stringify({ items: [{ productId, quantity: 1 }] }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId,
                'X-Queue-Token': token,
            },
            timeout: '10s',
        }
    );

    orderDuration.add(orderRes.timings.duration);
    const orderOk = check(orderRes, { '주문 201': (r) => r.status === 201 });
    if (orderOk) orderSuccess.add(1);
    orderError.add(!orderOk);
}

// Full: 전체 흐름 시뮬레이션
// → 목적: 실제 사용 패턴에서의 병목 위치 파악
function runFullFlow(userId, productId) {
    // 1. 대기열 진입
    const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter?userId=${userId}`);
    enterDuration.add(enterRes.timings.duration);
    queueError.add(enterRes.status !== 200);

    if (enterRes.status !== 200) return;

    // 2. Adaptive Polling — nextPollAfterSeconds를 따름
    let token = null;
    let pollCount = 0;
    const MAX_POLLS = 6; // 최대 6회 폴링 (30초 가정)

    while (!token && pollCount < MAX_POLLS) {
        const pollRes = http.get(`${BASE_URL}/api/v1/queue/position?userId=${userId}`);
        pollDuration.add(pollRes.timings.duration);
        queueError.add(pollRes.status !== 200);

        if (pollRes.status === 200) {
            const body = pollRes.json();
            if (body.data && body.data.token) {
                token = body.data.token;
                tokenReceived.add(1);
                break;
            }
            // 서버가 알려준 주기만큼 대기
            const waitSeconds = (body.data && body.data.nextPollAfterSeconds) || 5;
            sleep(Math.min(waitSeconds, 5)); // 테스트에서는 최대 5초로 제한
        }
        pollCount++;
    }

    if (!token) return;

    // 3. 토큰으로 주문
    const orderRes = http.post(
        `${BASE_URL}/api/v1/orders`,
        JSON.stringify({ items: [{ productId, quantity: 1 }] }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-User-Id': userId,
                'X-Queue-Token': token,
            },
            timeout: '10s',
        }
    );

    orderDuration.add(orderRes.timings.duration);
    if (orderRes.status === 201) orderSuccess.add(1);
    orderError.add(orderRes.status !== 201 && orderRes.status !== 400); // 재고부족(400)은 정상
}

// ── teardown: 결과 요약 ────────────────────────────────────────
export function teardown() {
    console.log('');
    console.log('=== 대기열 한계점 탐색 결과 ===');
    console.log(`Phase: ${PHASE}`);
    console.log('');
    if (PHASE === '1') {
        console.log('▶ queue_enter_duration p(99) > 500ms 구간 = Redis 병목 지점');
        console.log('  → Redis는 싱글스레드이므로 보통 수천 TPS까지 견딤');
        console.log('  → p(99) 튀는 구간이 있으면 네트워크 문제 의심');
    } else if (PHASE === '2') {
        console.log('▶ order_duration p(99) > 3000ms 구간 = HikariCP 병목 지점');
        console.log('  → HikariCP max 40 기준, VU 40 초과 시 대기 발생 예상');
        console.log('  → VU 80(=BATCH_SIZE) 에서 p(99) 확인 → BATCH_SIZE 적정성 판단');
    } else {
        console.log('▶ queue_enter_duration / queue_poll_duration: Redis 응답속도');
        console.log('▶ order_duration: DB 처리속도 (HikariCP 병목 여부)');
        console.log('▶ token_received vs order_success: 토큰 발급 대비 주문 완료율');
    }
    console.log('');
    console.log('Grafana에서 hikaricp_connections_active 함께 확인 권장');
}