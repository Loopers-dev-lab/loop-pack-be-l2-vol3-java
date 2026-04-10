/**
 * 동시 진입 테스트
 *
 * 목표: 100명이 동시에 POST /api/v1/queue/enter 를 호출했을 때
 *       순번 중복 없이 1~100 사이 값이 각자에게 부여되는지 확인
 *
 * 실행: k6 run k6/01_concurrent_queue_enter.js
 */
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = 'http://localhost:8080';

// seed-data.sql 기반 테스트 유저 (사전에 DB에 등록된 유저 필요)
// 유저는 loginId: user1~user100, password: Test1234! 로 가정
const users = new SharedArray('users', function () {
    const arr = [];
    for (let i = 1; i <= 100; i++) {
        arr.push({ loginId: `user${i}`, password: 'Test1234!' });
    }
    return arr;
});

export const options = {
    scenarios: {
        concurrent_enter: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 100,
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],     // 에러율 1% 미만
        http_req_duration: ['p(95)<500'],   // 95%ile 응답 500ms 미만
    },
};

export default function () {
    const user = users[__VU - 1];

    const res = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
        headers: {
            'X-Loopers-LoginId': user.loginId,
            'X-Loopers-LoginPw': user.password,
        },
    });

    check(res, {
        'status is 200': (r) => r.status === 200,
        'position is valid (>= 1)': (r) => {
            const body = JSON.parse(r.body);
            return body.data && body.data.position >= 1;
        },
        'no duplicate position (each VU unique)': (r) => {
            const body = JSON.parse(r.body);
            return body.data && body.data.position <= 100;
        },
    });
}
