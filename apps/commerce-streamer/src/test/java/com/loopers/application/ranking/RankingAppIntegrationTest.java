package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
@DisplayName("RankingApp 통합 테스트 — Redis ZSET 반영 + 가중치 검증")
class RankingAppIntegrationTest {

    @Autowired
    private RankingApp rankingApp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @Nested
    @DisplayName("applyLikeDelta - Like 이벤트 점수 반영")
    class ApplyLikeDelta {

        @Test
        @DisplayName("LikedEvent → +0.2 점수 누적")
        void likedEventIncreasesZsetScore() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            rankingApp.applyLikeDelta(42L, 1, date);
            rankingApp.applyLikeDelta(42L, 1, date);
            rankingApp.applyLikeDelta(42L, 1, date);

            Double score = redisTemplate.opsForZSet().score(key, "42");
            assertThat(score).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("LikeRemovedEvent(delta=-1) → 차감 없음 (멘토링 피드백)")
        void likeRemovedEventDoesNotDeduceScore() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            rankingApp.applyLikeDelta(42L, 1, date);
            rankingApp.applyLikeDelta(42L, 1, date);
            Double scoreBeforeUnlike = redisTemplate.opsForZSet().score(key, "42");

            rankingApp.applyLikeDelta(42L, -1, date);

            Double scoreAfterUnlike = redisTemplate.opsForZSet().score(key, "42");
            assertThat(scoreAfterUnlike).isEqualTo(scoreBeforeUnlike);
        }
    }

    @Nested
    @DisplayName("applyOrderScore - Order 이벤트 점수 반영 (price * amount)")
    class ApplyOrderScore {

        @Test
        @DisplayName("주문 1건(price=10000, qty=2) → 0.7 * 10000 * 2 = 14000")
        void orderScoreIsOrderWeightTimesPriceTimesQuantity() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            rankingApp.applyOrderScore(42L, new BigDecimal("10000"), 2, date);

            Double score = redisTemplate.opsForZSet().score(key, "42");
            assertThat(score).isCloseTo(14000.0, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    @Nested
    @DisplayName("applyViewScore - View 이벤트 점수 반영")
    class ApplyViewScore {

        @Test
        @DisplayName("View 1회 → 0.1 점수 증가")
        void viewScoreAddsViewWeight() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            rankingApp.applyViewScore(42L, date);
            rankingApp.applyViewScore(42L, date);

            Double score = redisTemplate.opsForZSet().score(key, "42");
            assertThat(score).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    @Nested
    @DisplayName("가중치 복합 시나리오 검증 (발제 체크리스트)")
    class WeightScenarios {

        @Test
        @DisplayName("주문 1건(price=10000, qty=1) > 좋아요 3건")
        void orderOnceBeatsLikeThreeTimes() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            rankingApp.applyOrderScore(1L, new BigDecimal("10000"), 1, date);
            rankingApp.applyLikeDelta(2L, 1, date);
            rankingApp.applyLikeDelta(2L, 1, date);
            rankingApp.applyLikeDelta(2L, 1, date);

            Double scoreA = redisTemplate.opsForZSet().score(key, "1");
            Double scoreB = redisTemplate.opsForZSet().score(key, "2");

            assertThat(scoreA).isGreaterThan(scoreB);
            assertThat(scoreA).isCloseTo(7000.0, org.assertj.core.data.Offset.offset(0.01));
            assertThat(scoreB).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("조회 100건(score≈10) vs 좋아요 50건(score≈10) — 가중치 교차 검증")
        void viewHundredApproxEqualsLikeFifty() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            for (int i = 0; i < 100; i++) rankingApp.applyViewScore(1L, date);
            for (int i = 0; i < 50; i++) rankingApp.applyLikeDelta(2L, 1, date);

            Double scoreA = redisTemplate.opsForZSet().score(key, "1");
            Double scoreB = redisTemplate.opsForZSet().score(key, "2");

            assertThat(scoreA).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.1));
            assertThat(scoreB).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.1));
        }

        @Test
        @DisplayName("Top-N 정렬 확인 — ZREVRANGE가 score 내림차순으로 반환")
        void topNSortedByScoreDesc() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            String key = RankingKeyGenerator.dailyKey(date);

            // score: A=7000 (주문 10000*1), B=140 (주문 200*1), C=0.6 (좋아요 3)
            rankingApp.applyOrderScore(1L, new BigDecimal("10000"), 1, date);
            rankingApp.applyOrderScore(2L, new BigDecimal("200"), 1, date);
            rankingApp.applyLikeDelta(3L, 1, date);
            rankingApp.applyLikeDelta(3L, 1, date);
            rankingApp.applyLikeDelta(3L, 1, date);

            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 10);

            assertThat(tuples).hasSize(3);
            String[] expectedOrder = {"1", "2", "3"};
            int i = 0;
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                assertThat(tuple.getValue()).isEqualTo(expectedOrder[i++]);
            }
        }
    }
}
