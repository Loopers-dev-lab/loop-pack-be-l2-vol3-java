/**
 * 토큰 만료 테스트
 *
 * 목표: 토큰 발급 후 TTL(5분) 초과 시 주문 API 호출이 실패하는지 확인
 *       (테스트 편의상 서버 TTL을 10초로 줄이거나, sleep으로 시뮬레이션)
 *
 * 시나리오:
 *   1. 대기열 진입 → 순번 조회 반복 → 토큰 수신
 *   2. sleep(310) — 5분 + 10초 대기 (토큰 만료)
 *   3. 만료된 토큰으로 주문 API 호출 → 400 Bad Request 확인
 *
 * 실행: k6 run k6/02_token_expiry.js
 *
 * 주의: 실제 5분 TTL 테스트 시 maxDuration을 충분히 늘려야 함
 *       빠른 검증이 필요하다면 application.yml 의 토큰 TTL을 10초로 낮춰 테스트
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8080';

// 테스트용 단일 유저
const LOGIN_ID = 'tokenuser';
const PASSWORD = 'Test1234!';

// 테스트용 상품 ID (seed-data.sql 기반)
const PRODUCT_ID = 1;

export const options = {
    vus: 1,
    iterations: 1,
    maxDuration: '10m',
};

export default function () {
    // Step 1: 대기열 진입
    const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`, null, {
        headers: {
            'X-Loopers-LoginId': LOGIN_ID,
            'X-Loopers-LoginPw': PASSWORD,
        },
    });
    check(enterRes, { '1. 대기열 진입 성공': (r) => r.status === 200 });

    // Step 2: 토큰 수신까지 폴링 (최대 2분)
    let token = null;
    for (let i = 0; i < 120; i++) {
        sleep(1);
        const posRes = http.get(`${BASE_URL}/api/v1/queue/position`, {
            headers: {
                'X-Loopers-LoginId': LOGIN_ID,
                'X-Loopers-LoginPw': PASSWORD,
            },
        });

        if (posRes.status === 200) {
            const body = JSON.parse(posRes.body);
            if (body.data && body.data.token) {
                token = body.data.token;
                console.log(`토큰 수신: ${token}`);
                break;
            }
        }
    }

    check(token, { '2. 토큰 수신 성공': (t) => t !== null });
    if (!token) return;

    // Step 3: 토큰 만료 대기 (5분 10초)
    console.log('토큰 만료 대기 중 (310초)...');
    sleep(310);

    // Step 4: 만료된 토큰으로 주문 시도 → 실패 확인
    const orderRes = http.post(
        `${BASE_URL}/api/v1/orders`,
        JSON.stringify({ items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
        {
            headers: {
                'Content-Type': 'application/json',
                'X-Loopers-LoginId': LOGIN_ID,
                'X-Loopers-LoginPw': PASSWORD,
                'X-Entry-Token': token,
            },
        }
    );

    check(orderRes, {
        '3. 만료된 토큰으로 주문 실패 (400)': (r) => r.status === 400,
    });
}
