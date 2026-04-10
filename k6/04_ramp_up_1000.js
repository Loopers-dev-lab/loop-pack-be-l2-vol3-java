/**
 * Ramp-up 부하 테스트 (한계 탐색)
 *
 * 목표: 1000명까지 점진적으로 트래픽을 올려
 *       시스템이 어느 지점에서 병목/에러가 발생하는지 확인
 *
 * 시나리오:
 *   - 0 → 200명 (30s): 워밍업
 *   - 200 → 500명 (60s): 중간 부하
 *   - 500 → 1000명 (60s): 고부하 (breaking point 탐색)
 *   - 1000명 유지 (60s): 지속 내구성 확인
 *   - 1000 → 0명 (30s): 종료
 *
 * 관찰 포인트:
 *   - 응답시간이 급격히 늘어나는 구간
 *   - 에러율이 올라가기 시작하는 VU 수
 *   - queue_depth 가 줄지 않고 계속 쌓이는지 여부
 *
 * 실행: k6 run k6/04_ramp_up_1000.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

// 테스트 전 user1~user100 회원 가입 (이미 존재하면 무시)
export function setup() {
    const requests = [];
    for (let i = 1; i <= 100; i++) {
        requests.push([
            'POST',
            `${BASE_URL}/api/v1/users`,
            JSON.stringify({
                loginId: `user${i}`,
                password: 'Test1234!',
                name: `유저${i}`,
                birthday: '19900101',
                email: `user${i}@test.com`,
            }),
            { headers: { 'Content-Type': 'application/json' } },
        ]);
    }
    http.batch(requests);
}

const queueDepth = new Trend('queue_depth');
const enterSuccess = new Counter('enter_success');
const enterFail = new Counter('enter_fail');
const errorRate = new Rate('error_rate');

export const options = {
    setupTimeout: '120s',
    scenarios: {
        ramp_up: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 200 },   // 워밍업
                { duration: '60s', target: 500 },   // 중간 부하
                { duration: '60s', target: 1000 },  // 고부하 (breaking point 탐색)
                { duration: '60s', target: 1000 },  // 지속 내구성
                { duration: '30s', target: 0 },     // 종료
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],         // 에러율 5% 미만
        http_req_duration: ['p(95)<2000'],      // 95%ile 응답 2초 미만
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
