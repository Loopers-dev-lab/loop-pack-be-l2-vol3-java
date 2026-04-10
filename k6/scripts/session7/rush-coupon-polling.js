import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * Step 3 — 발급 결과 polling 부하 테스트
 *
 * rush-coupon-spike.js 실행 후, 결과 조회 API를 200 VU로 polling.
 * Consumer 처리 전(404) → 처리 후(200) 전환 시간을 측정한다.
 *
 * 사전 조건: rush-coupon-spike.js가 먼저 실행되어 requestId가 생성된 상태
 * (이 테스트는 임의 requestId로 polling 부하를 측정 — 404 응답 시간도 포함)
 *
 * 관찰 포인트:
 *   - polling 응답 p95 < 50ms
 *   - 404 (처리 중) → 200 (결과) 전환까지 평균 시간
 *
 * 실행:
 *   docker run --rm -i --network host \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/session7/rush-coupon-polling.js
 */

const RUSH_COUPON_ID = __ENV.RUSH_COUPON_ID || '1';

export const options = {
    scenarios: {
        polling: {
            executor: 'constant-vus',
            vus: 200,
            duration: '1m',
        },
    },
    thresholds: {
        'poll_duration': ['p(95)<50'],
    },
};

const BASE = 'http://localhost:8080';

const pollHit = new Counter('poll_hit');        // 200 (결과 존재)
const pollMiss = new Counter('poll_miss');      // 404 (처리 중)
const pollError = new Counter('poll_error');
const pollDuration = new Trend('poll_duration');

export default function () {
    const vuId = (__VU % 1000) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');

    // 발급 요청 → requestId 획득 → polling
    const issueRes = http.post(
        `${BASE}/api/v1/coupons/${RUSH_COUPON_ID}/rush-issue`,
        null,
        { headers: HEADERS }
    );

    let requestId;
    if (issueRes.status === 202) {
        try {
            requestId = JSON.parse(issueRes.body).data.requestId;
        } catch (e) { /* ignore */ }
    }

    if (!requestId) {
        sleep(0.5);
        return;
    }

    // polling (최대 10회, 0.5초 간격)
    for (let i = 0; i < 10; i++) {
        sleep(0.5);
        const start = Date.now();
        const res = http.get(
            `${BASE}/api/v1/coupons/issue-result/${requestId}`,
            { headers: HEADERS }
        );
        pollDuration.add(Date.now() - start);

        if (res.status === 200) {
            pollHit.add(1);
            break;
        } else if (res.status === 404) {
            pollMiss.add(1);
        } else {
            pollError.add(1);
            break;
        }
    }
}
