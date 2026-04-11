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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
@DisplayName("멘토링 피드백 반영 검증 테스트")
class RankingMentoringFeedbackTest {

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
    @DisplayName("피드백 B: Like 차감 제거 — LikeRemoved 시 점수 유지")
    class LikeNoDeduce {

        @Test
        @DisplayName("LikeRemoved(delta=-1) 시 랭킹 점수가 차감되지 않음")
        void likeRemovedDoesNotDeduceScore() {
            LocalDate date = LocalDate.of(2026, 4, 6);
            String key = RankingKeyGenerator.dailyKey(date);

            rankingApp.applyLikeDelta(1L, 1, date);
            Double scoreAfterLike = redisTemplate.opsForZSet().score(key, "1");
            assertThat(scoreAfterLike).isNotNull();
            assertThat(scoreAfterLike).isGreaterThan(0);

            rankingApp.applyLikeDelta(1L, -1, date);
            Double scoreAfterUnlike = redisTemplate.opsForZSet().score(key, "1");

            assertThat(scoreAfterUnlike).isEqualTo(scoreAfterLike);
            System.out.println("[LIKE_NO_DEDUCE] Like +1 score=" + scoreAfterLike
                    + ", Like -1 후 score=" + scoreAfterUnlike + " (변화 없음 ✓)");
        }

        @Test
        @DisplayName("좋아요 3번 → 취소 2번 → 점수는 3번 좋아요 점수 그대로")
        void multipleUnlikesDoNotReduce() {
            LocalDate date = LocalDate.of(2026, 4, 6);
            String key = RankingKeyGenerator.dailyKey(date);

            rankingApp.applyLikeDelta(1L, 1, date);
            rankingApp.applyLikeDelta(1L, 1, date);
            rankingApp.applyLikeDelta(1L, 1, date);
            Double scoreAfter3Likes = redisTemplate.opsForZSet().score(key, "1");

            rankingApp.applyLikeDelta(1L, -1, date);
            rankingApp.applyLikeDelta(1L, -1, date);
            Double scoreAfter2Unlikes = redisTemplate.opsForZSet().score(key, "1");

            assertThat(scoreAfter2Unlikes).isEqualTo(scoreAfter3Likes);
            System.out.println("[MULTI_UNLIKE] 3 likes score=" + scoreAfter3Likes
                    + ", 2 unlikes 후 score=" + scoreAfter2Unlikes + " (변화 없음 ✓)");
        }
    }

    @Nested
    @DisplayName("피드백 A: tie-break — 동점 상품 최신 이벤트 상위")
    class TieBreak {

        @Test
        @DisplayName("동일 base score 시 나중에 이벤트 발생한 상품이 미세하게 높은 점수")
        void laterEventGetsSlightlyHigherScore() throws InterruptedException {
            LocalDate date = LocalDate.of(2026, 4, 6);
            String key = RankingKeyGenerator.dailyKey(date);

            rankingApp.applyViewScore(1L, date);
            Thread.sleep(1100);
            rankingApp.applyViewScore(2L, date);

            Double score1 = redisTemplate.opsForZSet().score(key, "1");
            Double score2 = redisTemplate.opsForZSet().score(key, "2");

            assertThat(score1).isNotNull();
            assertThat(score2).isNotNull();
            assertThat(score2).isGreaterThan(score1);

            double diff = score2 - score1;
            assertThat(diff).isLessThan(0.01);

            System.out.println("[TIE_BREAK] product1 score=" + score1 + ", product2 score=" + score2);
            System.out.printf("[TIE_BREAK] diff=%.9f (< 0.01 = base score에 영향 없음 ✓)%n", diff);
        }

        @Test
        @DisplayName("tie-break는 base score 차이를 뒤집지 않음")
        void tieBreakDoesNotOverrideBaseScore() {
            LocalDate date = LocalDate.of(2026, 4, 6);
            String key = RankingKeyGenerator.dailyKey(date);

            rankingApp.applyOrderScore(1L, new BigDecimal("10000"), 1, date);

            rankingApp.applyViewScore(2L, date);
            rankingApp.applyViewScore(2L, date);

            Double orderScore = redisTemplate.opsForZSet().score(key, "1");
            Double viewScore = redisTemplate.opsForZSet().score(key, "2");

            assertThat(orderScore).isGreaterThan(viewScore);
            System.out.println("[TIE_BREAK_SAFETY] order score=" + orderScore + ", 2 views score=" + viewScore
                    + " → base score 차이가 tie-break보다 지배적 ✓");
        }
    }

    @Nested
    @DisplayName("Redis 실패 best-effort — 랭킹 실패 시 메트릭은 정상")
    class BestEffort {

        @Test
        @DisplayName("applyLikeDelta 호출 후 score가 정상 반영됨 (정상 케이스)")
        void normalCaseWorks() {
            LocalDate date = LocalDate.of(2026, 4, 6);
            String key = RankingKeyGenerator.dailyKey(date);

            rankingApp.applyLikeDelta(1L, 1, date);

            Double score = redisTemplate.opsForZSet().score(key, "1");
            assertThat(score).isNotNull();
            assertThat(score).isGreaterThan(0);
        }
    }
}
