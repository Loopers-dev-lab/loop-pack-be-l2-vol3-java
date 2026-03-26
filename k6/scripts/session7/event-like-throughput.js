import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { authHeaders } from '../../lib/helpers.js';

/**
 * Step 1 — 좋아요 처리량 테스트
 *
 * 이벤트 분리 후 좋아요 응답 시간이 개선되었는지 확인한다.
 * - SELECT FOR UPDATE 제거 → 병렬 처리 가능
 * - incrementLikeCount가 AFTER_COMMIT으로 분리 → 응답에서 제외
 *
 * 관찰 포인트:
 *   - p95 < 50ms (이벤트 분리 전 대비 개선)
 *   - 에러율 < 0.1%
 *   - 테스트 후 like_count 불일치 SQL로 검증
 *
 * 실행:
 *   docker run --rm -i --network host \
 *     -v $(pwd)/k6:/scripts grafana/k6 \
 *     run /scripts/scripts/session7/event-like-throughput.js
 */
export const options = {
    scenarios: {
        like_load: {
            executor: 'constant-vus',
            vus: 100,
            duration: '3m',
        },
    },
    thresholds: {
        'like_duration': ['p(95)<50', 'p(99)<100'],
        'like_error_rate': ['rate<0.001'],
    },
};

const BASE = 'http://localhost:8080';

const likeSuccess = new Counter('like_success');
const likeError = new Counter('like_error');
const likeDuration = new Trend('like_duration');
const likeErrorRate = new Counter('like_error_rate');
const unlikeSuccess = new Counter('unlike_success');

export default function () {
    const vuId = (__VU % 1000) + 1;
    const HEADERS = authHeaders(`k6user${vuId}`, 'Test1234!');
    const productId = Math.floor(Math.random() * 100) + 1;

    // 70% 좋아요, 30% 좋아요 취소 (기존 좋아요가 있으면)
    if (Math.random() < 0.7) {
        const start = Date.now();
        const res = http.post(
            `${BASE}/api/v1/likes`,
            JSON.stringify({ productId }),
            { headers: HEADERS }
        );
        likeDuration.add(Date.now() - start);

        if (res.status === 200) {
            likeSuccess.add(1);
        } else {
            likeError.add(1);
        }
    } else {
        const res = http.del(
            `${BASE}/api/v1/likes/${productId}`,
            null,
            { headers: HEADERS }
        );
        if (res.status === 200) {
            unlikeSuccess.add(1);
        }
    }

    sleep(0.05);
}
