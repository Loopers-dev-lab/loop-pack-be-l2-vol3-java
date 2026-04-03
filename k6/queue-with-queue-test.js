/**
 * 대기열 적용 후 검증 테스트
 *
 * 목적:
 *   baseline 테스트에서 찾은 Knee TPS로 배치 크기를 설정한 뒤,
 *   상류에 10만 명이 몰려도 DB P99가 임계점(500ms) 이하로 유지되는지 검증한다.
 *
 * 검증 포인트:
 *   - Queue Depth: 유입 속도 > 처리 속도면 계속 증가 → 배치 크기 늘려야 함
 *   - DB P99: 항상 500ms 이하여야 함 (Back-pressure Gate가 동작하는 증거)
 *   - 입장 전환율: 토큰 발급 후 실제 주문 완료 비율
 *
 * 흐름:
 *   1단계: 대규모 진입 — 10,000명이 짧은 시간에 대기열 진입
 *   2단계: 폴링 — admitted=true 될 때까지 순위 조회
 *   3단계: 주문 — 입장 허가 후 주문 API 호출
 *
 * 실행 전 확인사항:
 *   - 상품 재고 충분히 넣기 (10,000개 이상)
 *   - 대기열 서버 기동 확인
 *   - baseline 테스트로 KNEE_TPS 실측 후 아래 값 업데이트
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// baseline 테스트 실측 후 여기 업데이트
// 현재는 Little's Law 역산 초기값: 40conn / 0.2s * 0.7 = 140 TPS
const KNEE_TPS = 140;
const POLL_INTERVAL_SECONDS = 1; // 순번 1~100 기준 인터벌
const BATCH_SIZE = KNEE_TPS * POLL_INTERVAL_SECONDS; // 140명/배치

const queueDepth = new Counter('queue_depth_at_enter');
const admitDuration = new Trend('admit_duration_ms', true);   // 진입 → 입장 허가까지
const orderP99 = new Trend('order_p99', true);
const orderSuccessRate = new Rate('order_success_rate');
const tokenConversionRate = new Rate('token_conversion_rate'); // 토큰 → 주문 완료

export const options = {
    scenarios: {
        // 1단계: 대규모 진입 (상류 10,000명 시뮬레이션)
        queue_burst: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 500 },  // 빠르게 진입
                { duration: '30s', target: 1000 }, // peak
                { duration: '1m',  target: 500 },  // 유지
                { duration: '30s', target: 0 },
            ],
            exec: 'enterQueue',
        },
        // 2단계: 폴링 + 입장 허가 후 주문 (전체 플로우)
        queue_and_order: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 0 },    // 진입 후 시작
                { duration: '1m',  target: 200 },
                { duration: '3m',  target: 200 },  // steady state 확인
                { duration: '30s', target: 0 },
            ],
            exec: 'pollAndOrder',
        },
    },
    thresholds: {
        // 핵심: DB P99가 500ms 이하를 유지하면 Back-pressure가 동작하는 것
        'order_p99': ['p(99)<500'],
        // 주문 에러율 1% 미만
        'http_req_failed{scenario:queue_and_order}': ['rate<0.01'],
    },
};

const BASE_URL = 'http://localhost:8080';
const QUEUE_ID = 'bf-2024';
const PRODUCT_ID = 1;

// 1단계: 대기열 진입만
export function enterQueue() {
    const memberId = Math.floor(Math.random() * 100000) + 1;

    const res = http.post(`${BASE_URL}/api/v1/queue/enter`,
        JSON.stringify({ userId: memberId, queueId: QUEUE_ID }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(res, { '진입 성공': (r) => r.status === 200 });

    if (res.status === 200) {
        const totalSize = res.json('data.totalSize');
        if (totalSize) queueDepth.add(totalSize);
    }

    sleep(0.1);
}

// 2단계: 진입 → 폴링 → 주문 전체 플로우
export function pollAndOrder() {
    const memberId = Math.floor(Math.random() * 100000) + 1;

    // 대기열 진입
    const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`,
        JSON.stringify({ userId: memberId, queueId: QUEUE_ID }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (enterRes.status !== 200) {
        sleep(1);
        return;
    }

    const token = enterRes.json('data.token');
    const enterTime = Date.now();

    // 폴링 — admitted=true 될 때까지
    let admitted = false;
    let pollCount = 0;
    const MAX_POLLS = 60; // 최대 60회 (타임아웃 방지)

    while (!admitted && pollCount < MAX_POLLS) {
        const rank = enterRes.json('data.rank') || 9999;

        // 적응형 인터벌 (Thundering Herd 방지)
        let interval = POLL_INTERVAL_SECONDS;
        if (rank > 1000) interval = 5;
        else if (rank > 100) interval = 3;

        // Jitter: 인터벌의 0~30% 랜덤 추가
        const jitter = interval * Math.random() * 0.3;
        sleep(interval + jitter);

        const statusRes = http.get(
            `${BASE_URL}/api/v1/queue/status?token=${token}&queueId=${QUEUE_ID}`
        );

        if (statusRes.status !== 200) {
            pollCount++;
            continue;
        }

        admitted = statusRes.json('data.admitted') === true;
        pollCount++;
    }

    if (!admitted) {
        // 타임아웃 — 전환율에 반영
        tokenConversionRate.add(0);
        return;
    }

    // 입장 허가 소요 시간
    admitDuration.add(Date.now() - enterTime);

    // 주문 API 호출 — 여기가 DB P99 측정 포인트
    const orderStart = Date.now();
    const orderRes = http.post(`${BASE_URL}/api/v1/orders`,
        JSON.stringify({
            memberId: memberId,
            items: [{ productId: PRODUCT_ID, quantity: 1 }],
            couponId: null,
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    orderP99.add(Date.now() - orderStart);

    const orderOk = check(orderRes, {
        '주문 성공': (r) => r.status === 200,
    });

    orderSuccessRate.add(orderOk ? 1 : 0);
    tokenConversionRate.add(orderOk ? 1 : 0);
}
