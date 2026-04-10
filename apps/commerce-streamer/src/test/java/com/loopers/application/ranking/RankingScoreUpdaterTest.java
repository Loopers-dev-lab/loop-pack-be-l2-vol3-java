package com.loopers.application.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * RankingScoreUpdater의 score 계산 로직 단위 테스트.
 *
 * <p>수식: {@code W(view)×log₁₀(viewCount+1) + W(like)×log₁₀(likeCount+1) + W(order)×log₁₀(salesAmount+1) + productId×ε}</p>
 * <p>기본 가중치: view=0.1, like=0.2, order=0.7, ε=1e-10</p>
 */
@ExtendWith(MockitoExtension.class)
class RankingScoreUpdaterTest {

    @Mock
    private RedisTemplate<String, String> writeTemplate;

    private RankingScoreUpdater updater;

    private static final long PID = 1L;

    @BeforeEach
    void setUp() {
        RankingProperties properties = new RankingProperties(
            new RankingProperties.Weights(0.1, 0.2, 0.7), 0.1
        );
        updater = new RankingScoreUpdater(writeTemplate, properties);
    }

    @Nested
    @DisplayName("기본 score 계산")
    class BasicScoreCalculation {

        @Test
        @DisplayName("모든 메트릭이 0이면 주 score는 0.0 (tiebreaker만 남음)")
        void allZeros_returnsOnlyTiebreaker() {
            double score = updater.calculateScore(0, 0, 0, PID);

            assertThat(score).isCloseTo(PID * 1e-10, within(1e-15));
        }

        @Test
        @DisplayName("view만 있을 때 score ≈ 0.1 × log₁₀(viewCount+1)")
        void viewOnly() {
            double score = updater.calculateScore(99, 0, 0, PID);

            // 0.1 × log₁₀(100) = 0.2
            assertThat(score).isCloseTo(0.2, within(1e-9));
        }

        @Test
        @DisplayName("like만 있을 때 score ≈ 0.2 × log₁₀(likeCount+1)")
        void likeOnly() {
            double score = updater.calculateScore(0, 99, 0, PID);

            // 0.2 × log₁₀(100) = 0.4
            assertThat(score).isCloseTo(0.4, within(1e-9));
        }

        @Test
        @DisplayName("order만 있을 때 score ≈ 0.7 × log₁₀(salesAmount+1)")
        void orderOnly() {
            double score = updater.calculateScore(0, 0, 9999, PID);

            // 0.7 × log₁₀(10000) = 2.8
            assertThat(score).isCloseTo(2.8, within(1e-9));
        }
    }

    @Nested
    @DisplayName("가중치 순서 검증")
    class WeightOrdering {

        @Test
        @DisplayName("주문 1건(10000원) > 좋아요 3건 — order 가중치가 지배적")
        void order_beats_likes() {
            double scoreLikes = updater.calculateScore(0, 3, 0, PID);
            double scoreOrder = updater.calculateScore(0, 0, 10000, PID);

            assertThat(scoreOrder).isGreaterThan(scoreLikes);
        }

        @Test
        @DisplayName("좋아요 가중치 > 조회 가중치 — 같은 count일 때")
        void like_beats_view_sameCount() {
            double scoreView = updater.calculateScore(100, 0, 0, PID);
            double scoreLike = updater.calculateScore(0, 100, 0, PID);

            assertThat(scoreLike).isGreaterThan(scoreView);
        }

        @Test
        @DisplayName("복합 score: 조회 100 + 좋아요 10 + 주문 50000원")
        void compositeScore() {
            double score = updater.calculateScore(100, 10, 50000, PID);

            double expected = 0.1 * Math.log10(101)
                + 0.2 * Math.log10(11)
                + 0.7 * Math.log10(50001)
                + PID * 1e-10;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }
    }

    @Nested
    @DisplayName("log₁₀ 정규화 효과")
    class LogNormalization {

        @Test
        @DisplayName("view 10배 차이(100 vs 1000)가 score에서는 1.5배 미만 차이")
        void logReducesScaleDifference() {
            double score100 = updater.calculateScore(100, 0, 0, PID);
            double score1000 = updater.calculateScore(1000, 0, 0, PID);

            assertThat(score1000).isGreaterThan(score100);
            assertThat(score1000 / score100).isLessThan(1.5);
        }

        @Test
        @DisplayName("salesAmount 100배 차이(1000 vs 100000)가 score에서 완화됨")
        void logReducesSalesAmountDominance() {
            double scoreLow = updater.calculateScore(0, 0, 1000, PID);
            double scoreHigh = updater.calculateScore(0, 0, 100000, PID);

            assertThat(scoreHigh).isGreaterThan(scoreLow);
            assertThat(scoreHigh / scoreLow).isLessThan(2.0);
        }
    }

    @Nested
    @DisplayName("음수 메트릭 방어")
    class NegativeMetricDefense {

        @Test
        @DisplayName("음수 viewCount → 0으로 클램핑되어 주 score 기여 0.0")
        void negativeViewCount_clampedToZero() {
            double score = updater.calculateScore(-5, 0, 0, PID);

            assertThat(score).isCloseTo(PID * 1e-10, within(1e-15));
        }

        @Test
        @DisplayName("음수 likeCount → 0으로 클램핑")
        void negativeLikeCount_clampedToZero() {
            double score = updater.calculateScore(0, -10, 0, PID);

            assertThat(score).isCloseTo(PID * 1e-10, within(1e-15));
        }

        @Test
        @DisplayName("음수 salesAmount → 0으로 클램핑")
        void negativeSalesAmount_clampedToZero() {
            double score = updater.calculateScore(0, 0, -50000, PID);

            assertThat(score).isCloseTo(PID * 1e-10, within(1e-15));
        }

        @Test
        @DisplayName("모든 메트릭 음수 → score는 메트릭 0일 때와 동일")
        void allNegative_equalToZeroMetrics() {
            double score = updater.calculateScore(-5, -10, -50000, PID);
            double scoreZero = updater.calculateScore(0, 0, 0, PID);

            assertThat(score).isEqualTo(scoreZero);
        }

        @Test
        @DisplayName("음수 메트릭이 양수 메트릭의 score를 침범하지 않음")
        void negativeDoesNotAffectPositiveTerms() {
            double scoreWithNegative = updater.calculateScore(100, -5, 0, PID);
            double scoreViewOnly = updater.calculateScore(100, 0, 0, PID);

            assertThat(scoreWithNegative).isEqualTo(scoreViewOnly);
        }
    }

    @Nested
    @DisplayName("타이브레이커 — productId × ε")
    class Tiebreaker {

        @Test
        @DisplayName("동점 시 높은 productId(신상품)가 상위")
        void sameMetrics_higherProductId_higherScore() {
            double scoreOld = updater.calculateScore(1, 0, 0, 101);
            double scoreNew = updater.calculateScore(1, 0, 0, 505);

            assertThat(scoreNew).isGreaterThan(scoreOld);
        }

        @Test
        @DisplayName("주 score가 다르면 productId가 높아도 역전 불가")
        void differentMetrics_productIdCannotReverse() {
            // product 101: view=2 → 주 score = 0.1×log₁₀(3) ≈ 0.0477
            double scoreHighMetric = updater.calculateScore(2, 0, 0, 101);
            // product 999999: view=1 → 주 score = 0.1×log₁₀(2) ≈ 0.0301
            double scoreLowMetric = updater.calculateScore(1, 0, 0, 999_999);

            assertThat(scoreHighMetric).isGreaterThan(scoreLowMetric);
        }

        @Test
        @DisplayName("productId 1000만이어도 tiebreaker는 주 score 최소 차이의 3.3%")
        void epsilon_doesNotExceedMinScoreDifference() {
            double tiebreakerMax = 10_000_000 * RankingScoreUpdater.TIEBREAKER_EPSILON;
            // 주 score 최소 유의미 차이: view 0→1 = 0.1 × log₁₀(2) ≈ 0.0301
            double minScoreDiff = 0.1 * Math.log10(2);

            assertThat(tiebreakerMax / minScoreDiff).isLessThan(0.05);
        }

        @Test
        @DisplayName("ε 상수가 1e-10")
        void epsilonConstant() {
            assertThat(RankingScoreUpdater.TIEBREAKER_EPSILON).isEqualTo(1e-10);
        }
    }

    @Nested
    @DisplayName("커스텀 가중치")
    class CustomWeights {

        @Test
        @DisplayName("가중치를 변경하면 score 비율이 달라짐")
        void differentWeights_changePriority() {
            RankingProperties viewFirst = new RankingProperties(
                new RankingProperties.Weights(0.7, 0.2, 0.1), 0.1
            );
            RankingScoreUpdater viewUpdater = new RankingScoreUpdater(writeTemplate, viewFirst);

            double scoreView = viewUpdater.calculateScore(100, 0, 0, PID);
            double scoreOrder = viewUpdater.calculateScore(0, 0, 100, PID);

            assertThat(scoreView).isGreaterThan(scoreOrder);
        }
    }

    @Nested
    @DisplayName("키 생성")
    class KeyGeneration {

        @Test
        @DisplayName("ZSET 키: ranking:all:{yyyyMMdd} 형식")
        void zsetKey_format() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            String key = RankingScoreUpdater.zsetKey(date);

            assertThat(key).isEqualTo("ranking:all:20260410");
        }

        @Test
        @DisplayName("Hash 키: ranking:metrics:{yyyyMMdd}:{productId} 형식")
        void hashKey_format() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            String key = RankingScoreUpdater.hashKey(date, 101L);

            assertThat(key).isEqualTo("ranking:metrics:20260410:101");
        }

        @Test
        @DisplayName("날짜가 다르면 다른 키 생성")
        void differentDates_differentKeys() {
            LocalDate day1 = LocalDate.of(2026, 4, 10);
            LocalDate day2 = LocalDate.of(2026, 4, 11);

            assertThat(RankingScoreUpdater.zsetKey(day1))
                .isNotEqualTo(RankingScoreUpdater.zsetKey(day2));
            assertThat(RankingScoreUpdater.hashKey(day1, 101L))
                .isNotEqualTo(RankingScoreUpdater.hashKey(day2, 101L));
        }

        @Test
        @DisplayName("같은 날짜 + 다른 productId → 다른 Hash 키")
        void sameDate_differentProductId_differentHashKeys() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            assertThat(RankingScoreUpdater.hashKey(date, 101L))
                .isNotEqualTo(RankingScoreUpdater.hashKey(date, 202L));
        }

        @Test
        @DisplayName("ZSET 키 prefix가 공개 상수와 일치")
        void zsetKey_usesPublicPrefix() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            assertThat(RankingScoreUpdater.zsetKey(date))
                .startsWith(RankingScoreUpdater.RANKING_ZSET_PREFIX);
        }

        @Test
        @DisplayName("Hash 키 prefix가 공개 상수와 일치")
        void hashKey_usesPublicPrefix() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            assertThat(RankingScoreUpdater.hashKey(date, 101L))
                .startsWith(RankingScoreUpdater.RANKING_METRICS_PREFIX);
        }

        @Test
        @DisplayName("TTL 상수가 2일(172800초)")
        void ttlConstant_isTwoDays() {
            assertThat(RankingScoreUpdater.RANKING_TTL_SECONDS).isEqualTo(172_800L);
        }
    }
}
