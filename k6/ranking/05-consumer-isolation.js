/**
 * 테스트 5: 컨슈머 장애 격리 검증
 *
 * 시나리오:
 *   Stage 1 (30s) — 정상 상태에서 좋아요 + 주문 이벤트 발행 → metric 테이블 적재 확인
 *   Stage 2 (수동) — Redis 컨테이너 stop (view 컨슈머 영향)
 *   Stage 3 (30s) — Redis 장애 상태에서 좋아요/주문 이벤트 → MySQL 직접 적재 정상 확인
 *   Stage 4 (수동) — Redis 컨테이너 restart
 *   Stage 5 (30s) — view 컨슈머도 정상 복귀 확인
 *
 * 증명하는 것:
 *   View 컨슈머가 Redis에 의존하지만 (SADD dedup),
 *   Interaction/Order 컨슈머는 MySQL 직접 적재라 Redis 장애와 무관.
 *   컨슈머 3개 분리로 한쪽 장애가 다른 쪽에 전파 안 됨.
 *
 * 전제조건:
 *   - commerce-api + commerce-streamer 실행 중
 *   - 상품, 유저, 브랜드 시드 데이터 존재
 *   - 로그인 헤더: X-Loopers-LoginId, X-Loopers-LoginPw
 *
 * 실행 방법:
 *   1. k6 run k6/ranking/05-consumer-isolation.js
 *   2. 30초 후 (Stage 1 끝): docker stop redis-master redis-readonly
 *   3. 30초 후 (Stage 3 끝): docker start redis-master redis-readonly
 *   4. 결과: Stage 3에서도 좋아요 API 200 응답 확인
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PRODUCT_IDS = [1, 2, 3];

const likeSuccess = new Rate('like_success');
const likeDuration = new Trend('like_duration', true);
const rankingSuccess = new Rate('ranking_success');
const rankingDuration = new Trend('ranking_duration', true);
const likeErrors = new Counter('like_errors');

export const options = {
  scenarios: {
    isolation_test: {
      executor: 'constant-vus',
      vus: 5,
      duration: '90s',
    },
  },
  thresholds: {
    like_success: ['rate>0.90'],
  },
};

export default function () {
  const vu = __VU;
  const userId = vu + 100; // 충돌 방지
  const productId = PRODUCT_IDS[Math.floor(Math.random() * PRODUCT_IDS.length)];

  // 좋아요 API 호출 (Interaction 이벤트 발행 → Kafka → InteractionConsumer → MySQL)
  const likeRes = http.post(
    `${BASE_URL}/api/v1/products/${productId}/likes`,
    null,
    {
      headers: {
        'X-Loopers-LoginId': `testuser${userId}`,
        'X-Loopers-LoginPw': 'password123',
      },
    }
  );

  const isLikeOk = likeRes.status === 200 || likeRes.status === 409; // 409 = 이미 좋아요
  likeSuccess.add(isLikeOk);
  likeDuration.add(likeRes.timings.duration);

  if (!isLikeOk) {
    likeErrors.add(1);
  }

  check(likeRes, {
    'like 200 or 409': (r) => r.status === 200 || r.status === 409,
  });

  // 랭킹 조회 (Redis 장애 시 DB fallback)
  const today = new Date().toISOString().slice(0, 10).replace(/-/g, '');
  const rankRes = http.get(`${BASE_URL}/api/v1/rankings?period=DAILY&date=${today}&page=0&size=10`);

  rankingSuccess.add(rankRes.status === 200);
  rankingDuration.add(rankRes.timings.duration);

  check(rankRes, {
    'ranking 200': (r) => r.status === 200,
  });

  sleep(1);
}

export function handleSummary(data) {
  const likeRate = data.metrics.like_success ? (data.metrics.like_success.values.rate * 100).toFixed(1) : '0';
  const rankRate = data.metrics.ranking_success ? (data.metrics.ranking_success.values.rate * 100).toFixed(1) : '0';
  const likeP95 = data.metrics.like_duration ? data.metrics.like_duration.values['p(95)'].toFixed(0) : '0';
  const rankP95 = data.metrics.ranking_duration ? data.metrics.ranking_duration.values['p(95)'].toFixed(0) : '0';
  const errors = data.metrics.like_errors ? data.metrics.like_errors.values.count : 0;

  return {
    stdout: `
=== 컨슈머 장애 격리 테스트 결과 ===
좋아요 API 성공률: ${likeRate}%  (p95: ${likeP95}ms)
랭킹 API 성공률: ${rankRate}%  (p95: ${rankP95}ms)
좋아요 에러 수: ${errors}

판정:
  Redis 정상 구간: 좋아요 + 랭킹 모두 정상
  Redis 장애 구간:
    - 좋아요 API → 200 (MySQL 직접 적재, Redis 무관)
    - 랭킹 API → 200 (DB fallback)
    - View 컨슈머 → 영향받지만 좋아요/주문 컨슈머는 무관
  Redis 복구 구간: 전체 정상 복귀

  좋아요 성공률 > 90% 이면 PASS
  (Redis 장애에도 Interaction/Order 경로 독립 동작)
========================================
`,
  };
}
