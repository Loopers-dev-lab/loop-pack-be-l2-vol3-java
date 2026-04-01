import http from 'k6/http';
import { check, sleep } from 'k6';

/*
 * ============================================================
 * Thundering Herd 비교 실험
 * ============================================================
 *
 * 이 스크립트를 2번 실행한다:
 *
 * [실험 A] 1초에 140명 일괄 발급 (Thundering Herd 발생)
 *   1. application.yml 변경: queue.batch-size: 140
 *   2. EntryTokenScheduler 변경: @Scheduled(fixedRate = 1000)
 *   3. 앱 재시작 후 이 스크립트 실행
 *
 * [실험 B] 100ms마다 14명 분산 발급 (Thundering Herd 완화)
 *   1. application.yml 변경: queue.batch-size: 14
 *   2. EntryTokenScheduler 변경: @Scheduled(fixedRate = 100)
 *   3. 앱 재시작 후 이 스크립트 실행
 *
 * 핵심: 토큰 받은 유저가 DB를 타는 API를 호출한다.
 * 일괄 발급이면 140명이 동시에 DB를 때리고,
 * 분산 발급이면 14명씩 나눠서 DB를 때린다.
 * ============================================================
 */

const BASE_URL = 'http://localhost:8080';

export const options = {
    stages: [
        { duration: '5s', target: 300 },    // 300명 빠르게 대기열 진입
        { duration: '60s', target: 300 },   // 60초 유지 (토큰 발급 + DB 부하 관찰)
        { duration: '5s', target: 0 },      // 종료
    ],
};

export default function () {
    const userId = __VU * 100000 + __ITER;

    // ===== 1단계: 대기열 진입 =====
    const enterRes = http.post(
        `${BASE_URL}/api/v1/queue/enter`,
        JSON.stringify({ userId: userId }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    if (enterRes.status !== 200) return;

    // ===== 2단계: Polling — 토큰 받을 때까지 대기 =====
    let token = null;
    let pollCount = 0;

    while (token === null && pollCount < 30) {
        sleep(1);

        const posRes = http.get(
            `${BASE_URL}/api/v1/queue/position?userId=${userId}`
        );

        if (posRes.status === 200) {
            const body = JSON.parse(posRes.body);
            if (body.data && body.data.token) {
                token = body.data.token;
            }
        }
        pollCount++;
    }

    // ===== 3단계: 토큰 받으면 DB를 타는 API 연타 =====
    // 토큰 받은 유저가 "주문 전 상품 조회"를 하는 시나리오
    // 일괄 발급이면 140명이 동시에 이 구간에 진입 → DB 커넥션 스파이크
    // 분산 발급이면 14명씩 진입 → DB 커넥션 평탄
    if (token !== null) {
        for (let i = 0; i < 5; i++) {
            const dbRes = http.get(`${BASE_URL}/api/v1/products`);

            check(dbRes, {
                'DB 조회 성공': (r) => r.status === 200,
                'DB 응답 < 200ms': (r) => r.timings.duration < 200,
            });

            sleep(0.1);  // 100ms 간격으로 연타 (실제 유저보다 공격적)
        }
    }
}
