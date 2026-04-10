/**
 * 처리량 초과 테스트
 *
 * 목표: 배치 크기(18명/100ms = 175 TPS)를 초과하는 요청이 들어와도
 *       시스템이 안정적으로 동작하는지 확인 (대기열이 올바르게 순차 처리)
 *
 * 시나리오:
 *   - 500명이 초당 500건으로 대기열 진입 요청
 *   - 스케줄러는 100ms마다 18명씩만 처리
 *   - 대기열 깊이(queue depth)가 쌓이다가 점진적으로 감소하는지 확인
 *   - 에러율이 낮은지 확인 (대기열 적재는 성공해야 함)
 *
 * 실행: k6 run k6/03_throughput_exceed.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

const queueDepth = new Trend('queue_depth');
const enterSuccess = new Counter('enter_success');
const enterFail = new Counter('enter_fail');

export const options = {
    scenarios: {
        throughput_exceed: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 500 },   // 500명으로 증가
                { duration: '30s', target: 500 },   // 500명 유지
                { duration: '10s', target: 0 },     // 종료
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],         // 에러율 5% 미만
        http_req_duration: ['p(99)<1000'],      // 99%ile 응답 1초 미만
    },
};

export default function () {
    const loginId = `user${(__VU % 100) + 1}`;

    const res = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
        headers: {
            'X-Loopers-LoginId': loginId,
            'X-Loopers-LoginPw': 'Test1234!',
        },
    });

    if (res.status === 200) {
        enterSuccess.add(1);
        const body = JSON.parse(res.body);
        if (body.data) {
            queueDepth.add(body.data.totalCount);
        }
    } else if (res.status === 409) {
        // 이미 대기열에 있는 경우 — 정상 케이스
        enterSuccess.add(1);
    } else {
        enterFail.add(1);
        console.error(`진입 실패: status=${res.status}, body=${res.body}`);
    }
}
