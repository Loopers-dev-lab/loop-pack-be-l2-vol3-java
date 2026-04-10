/**
 * 스파이크 테스트 (순간 몰림)
 *
 * 목표: 1000명이 순간적으로 몰렸을 때 대기열이 안정적으로 처리되는지 확인
 *       티켓팅/선착순처럼 특정 시점에 트래픽이 집중되는 상황 시뮬레이션
 *
 * 시나리오:
 *   - 0 → 1000명 (5s): 순간 스파이크 (몰림 재현)
 *   - 1000명 유지 (30s): 대기열 적재 확인
 *   - 1000 → 0명 (5s): 종료
 *
 * 관찰 포인트:
 *   - 스파이크 직후 에러율 (대기열 적재 실패 여부)
 *   - queue_depth 최댓값 및 이후 감소 추세
 *   - 응답시간 스파이크 (p99)
 *
 * 실행: k6 run k6/05_spike_1000.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';


const queueDepth = new Trend('queue_depth');
const enterSuccess = new Counter('enter_success');
const enterFail = new Counter('enter_fail');
const errorRate = new Rate('error_rate');

export const options = {
    setupTimeout: '120s',
    scenarios: {
        spike: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '5s', target: 1000 },   // 순간 스파이크
                { duration: '30s', target: 1000 },  // 1000명 유지
                { duration: '5s', target: 0 },      // 종료
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],         // 에러율 5% 미만
        http_req_duration: ['p(99)<3000'],      // 99%ile 응답 3초 미만
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

    const isError = res.status !== 200 && res.status !== 409;
    errorRate.add(isError);

    if (res.status === 200) {
        enterSuccess.add(1);
        const body = JSON.parse(res.body);
        if (body.data && body.data.totalCount !== undefined) {
            queueDepth.add(body.data.totalCount);
        }
    } else if (res.status === 409) {
        // 이미 대기열에 있는 경우 — 정상 케이스
        enterSuccess.add(1);
    } else {
        enterFail.add(1);
        console.error(`진입 실패: status=${res.status}, body=${res.body}`);
    }

    check(res, {
        'status is 200 or 409': (r) => r.status === 200 || r.status === 409,
    });
}
