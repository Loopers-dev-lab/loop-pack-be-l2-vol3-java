import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    // 부하를 단계적으로 올린다 (갑자기 500명 넣으면 의미 없음)
    stages: [
        { duration: '10s', target: 100 },   // 0 → 100명 (워밍업)
        { duration: '30s', target: 100 },   // 100명 유지 (안정 구간)
        { duration: '10s', target: 500 },   // 100 → 500명 (부하 증가)
        { duration: '30s', target: 500 },   // 500명 유지 (피크 구간)
        { duration: '10s', target: 0 },     // 500 → 0명 (종료)
    ],
    //
    // 왜 단계적으로?
    // 실제 블프 트래픽도 "서서히 올라가다가 피크"를 찍는다.
    // 100명에서 안정적이면 → 500명에서 어떻게 변하는지 비교할 수 있다.
};

export default function () {
    // VU마다 고유한 userId 만들기
    // __VU = 현재 가상 유저 번호 (1, 2, 3, ...)
    // __ITER = 이 VU가 몇 번째 반복 중인지 (0, 1, 2, ...)
    // 둘을 조합하면 유니크한 userId가 된다
    const userId = __VU * 100000 + __ITER;

    // POST 요청 보내기
    const res = http.post(
        'http://localhost:8080/api/v1/queue/enter',
        JSON.stringify({ userId: userId }),
        {
            headers: { 'Content-Type': 'application/json' },
        }
    );

    // 응답 검증
    check(res, {
        '대기열 진입 성공 (200)': (r) => r.status === 200,
        '응답 시간 < 100ms': (r) => r.timings.duration < 100,
    });

    // 1초 쉬고 다음 요청 (실제 유저처럼)
    sleep(1);
}
