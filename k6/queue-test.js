import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const enterSuccess = new Counter('queue_enter_success');
const enterDuplicate = new Counter('queue_enter_duplicate');
const tokenIssued = new Counter('queue_token_issued');
const orderSuccess = new Counter('queue_order_success');
const orderForbidden = new Counter('queue_order_forbidden');

const BASE_URL = 'http://localhost:8080';

export const options = {
    scenarios: {
        // Phase 1: 500명 동시 대기열 진입
        queue_enter: {
            executor: 'shared-iterations',
            vus: 50,
            iterations: 500,
            maxDuration: '30s',
            exec: 'enterQueue',
        },
        // Phase 2: 진입 완료 후 순번 조회 + 토큰 대기
        poll_position: {
            executor: 'shared-iterations',
            vus: 20,
            iterations: 100,
            maxDuration: '30s',
            startTime: '5s',
            exec: 'pollPosition',
        },
        // Phase 3: 토큰 없이 주문 시도 (403 확인)
        order_without_token: {
            executor: 'shared-iterations',
            vus: 5,
            iterations: 5,
            maxDuration: '10s',
            startTime: '3s',
            exec: 'orderWithoutToken',
        },
    },
};

// Phase 1: 대기열 진입
export function enterQueue() {
    const userId = exec.scenario.iterationInTest + 1;

    const res = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
        headers: { 'X-User-Id': `${userId}` },
    });

    const success = check(res, {
        '대기열 진입 200 OK': (r) => r.status === 200,
    });

    if (success) {
        const body = JSON.parse(res.body);
        if (body.data.newEntry) {
            enterSuccess.add(1);
        } else {
            enterDuplicate.add(1);
        }
    }
}

// Phase 2: 순번 조회 + 토큰 확인
export function pollPosition() {
    const userId = exec.scenario.iterationInTest + 1;

    // 먼저 대기열 진입
    http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
        headers: { 'X-User-Id': `${userId}` },
    });

    // 순번 조회 (최대 10초 대기)
    for (let i = 0; i < 20; i++) {
        const res = http.get(`${BASE_URL}/api/v1/queue/position`, {
            headers: { 'X-User-Id': `${userId}` },
        });

        if (res.status === 200) {
            const body = JSON.parse(res.body);

            if (body.data.token !== null) {
                tokenIssued.add(1);

                // 토큰으로 주문 시도
                const orderRes = http.post(
                    `${BASE_URL}/api/v1/orders`,
                    JSON.stringify({ items: [{ productId: 1, quantity: 1 }] }),
                    {
                        headers: {
                            'X-User-Id': `${userId}`,
                            'X-Queue-Token': body.data.token,
                            'Content-Type': 'application/json',
                        },
                    }
                );

                // 상품이 없어서 실패할 수 있지만 403이 아니면 토큰 검증 통과
                if (orderRes.status !== 403) {
                    orderSuccess.add(1);
                }
                return;
            }

            // pollingInterval에 따라 대기
            const interval = body.data.pollingIntervalSeconds || 1;
            sleep(interval * 0.5); // 테스트이므로 절반만 대기
        } else {
            sleep(0.5);
        }
    }
}

// Phase 3: 토큰 없이 주문 → 403
export function orderWithoutToken() {
    const userId = 90000 + exec.scenario.iterationInTest;

    const res = http.post(
        `${BASE_URL}/api/v1/orders`,
        JSON.stringify({ items: [{ productId: 1, quantity: 1 }] }),
        {
            headers: {
                'X-User-Id': `${userId}`,
                'Content-Type': 'application/json',
            },
        }
    );

    check(res, {
        '토큰 없이 주문 → 403': (r) => r.status === 403,
    });

    if (res.status === 403) {
        orderForbidden.add(1);
    }
}

export function handleSummary(data) {
    const metrics = data.metrics;

    console.log('\n=== Redis 대기열 시스템 k6 테스트 결과 ===');
    console.log(`대기열 진입 성공: ${metrics.queue_enter_success ? metrics.queue_enter_success.values.count : 0}`);
    console.log(`대기열 중복 진입: ${metrics.queue_enter_duplicate ? metrics.queue_enter_duplicate.values.count : 0}`);
    console.log(`토큰 발급 확인: ${metrics.queue_token_issued ? metrics.queue_token_issued.values.count : 0}`);
    console.log(`토큰으로 주문 통과: ${metrics.queue_order_success ? metrics.queue_order_success.values.count : 0}`);
    console.log(`토큰 없이 주문 거부(403): ${metrics.queue_order_forbidden ? metrics.queue_order_forbidden.values.count : 0}`);

    return {};
}
