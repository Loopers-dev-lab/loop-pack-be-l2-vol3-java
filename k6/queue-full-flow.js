import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 50 },   // 50명까지 증가
        { duration: '60s', target: 50 },   // 50명 유지 (1분간 흐름 관찰)
        { duration: '10s', target: 0 },    // 종료
    ],
};

const BASE_URL = 'http://localhost:8080';

export default function () {
    // 유니크한 userId 생성
    const userId = __VU * 100000 + __ITER;

    // ===== 1단계: 대기열 진입 =====
    const enterRes = http.post(
        `${BASE_URL}/api/v1/queue/enter`,
        JSON.stringify({ userId: userId }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(enterRes, {
        '대기열 진입 성공': (r) => r.status === 200,
    });

    // 진입 실패하면 이 유저는 여기서 끝
    if (enterRes.status !== 200) return;

    // ===== 2단계: Polling — 토큰 받을 때까지 2초마다 순번 조회 =====
    let token = null;
    let pollCount = 0;

    while (token === null && pollCount < 30) {  // 최대 30번 = 60초
        sleep(2);  // 2초 대기

        const posRes = http.get(
            `${BASE_URL}/api/v1/queue/position?userId=${userId}`
        );

        check(posRes, {
            '순번 조회 성공': (r) => r.status === 200,
        });

        // 응답에서 토큰 확인
        if (posRes.status === 200) {
            const body = JSON.parse(posRes.body);
            // body.data.token이 있으면 = 내 차례!
            if (body.data && body.data.token) {
                token = body.data.token;
            }
        }

        pollCount++;
    }

    // ===== 3단계: 결과 =====
    if (token !== null) {
        console.log(`userId=${userId}: 토큰 발급! (${pollCount}번 polling)`);
        // 여기서 POST /api/v1/orders를 호출할 수도 있지만,
        // 주문은 상품/회원 데이터가 필요하므로 토큰 발급 확인까지만 테스트
    } else {
        console.log(`userId=${userId}: 토큰 미발급 (60초 초과)`);
    }
}
