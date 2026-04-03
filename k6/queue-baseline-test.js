/**
 * 하류 처리 한계 테스트 — Knee of the Curve (대기열 없음)
 *
 * 목적:
 *   대기열 없이 주문 API를 직접 부하 테스트해서
 *   P99 > 500ms 진입 시점의 동시 연결 수(= Knee)를 찾는다.
 *   이 값이 배치 크기 계산의 기준 TPS가 된다.
 *
 * 예상 Knee:
 *   HikariCP pool-size = 40 이므로, 40 VU 근처에서 P99가 급등할 것으로 예상.
 *   실측으로 확인 필요.
 *
 * 테스트 순서:
 *   1. 이 스크립트로 Knee 찾기 → 임계 TPS(T) 기록
 *   2. 배치 크기 = T × 폴링 인터벌(초)
 *   3. queue-with-queue-test.js로 대기열 붙인 후 P99 안정 검증
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const orderP99 = new Trend('order_p99', true);
const orderSuccessRate = new Rate('order_success_rate');

export const options = {
    stages: [
        { duration: '1m',  target: 10  }, // warm-up
        { duration: '2m',  target: 20  }, // 안전 구간
        { duration: '2m',  target: 30  }, // pool 75% 수준
        { duration: '2m',  target: 40  }, // pool 한계 도달 — knee 예상 지점
        { duration: '2m',  target: 50  }, // pool 초과 — P99 급등 예상
        { duration: '2m',  target: 70  }, // 위험 구간 확인
        { duration: '1m',  target: 0   }, // ramp-down
    ],
    thresholds: {
        // 임계값: P99 500ms. 이 선 넘는 시점 = Knee.
        // 테스트 통과/실패 기준이 아니라 그래프에서 확인하는 용도.
        'http_req_duration{p:99}': ['p(99)<500'],
        'http_req_failed': ['rate<0.05'],
    },
};

const BASE_URL = 'http://localhost:8080';

// 테스트용 상품 ID: 사전에 재고 충분히 넣어둘 것
const PRODUCT_ID = 1;
const MEMBER_ID = 1;

export default function () {
    const payload = JSON.stringify({
        memberId: MEMBER_ID,
        items: [{ productId: PRODUCT_ID, quantity: 1 }],
        couponId: null,
    });

    const start = Date.now();
    const res = http.post(`${BASE_URL}/api/v1/orders`, payload, {
        headers: { 'Content-Type': 'application/json' },
    });
    const duration = Date.now() - start;

    orderP99.add(duration);

    const ok = check(res, {
        '주문 성공 (200)': (r) => r.status === 200,
        'result = SUCCESS': (r) => {
            try { return r.json('meta.result') === 'SUCCESS'; }
            catch { return false; }
        },
    });

    orderSuccessRate.add(ok ? 1 : 0);

    // VU당 요청 간격 없음 — 최대한 연결 수를 채워야 Knee가 보임
    // sleep 넣으면 VU가 커도 실제 동시 연결 수는 낮아져 곡선이 안 나옴
}
