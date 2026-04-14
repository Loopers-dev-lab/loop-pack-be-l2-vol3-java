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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * RankingScoreUpdater의 score 계산 로직 단위 테스트.
 *
 * <p>수식 (v2 — 0~1 정규화):
 * {@code categoryPriority + W(view)×log₁₀(viewCount+1)/MAX_LOG + W(like)×log₁₀(likeCount+1)/MAX_LOG
 *   + W(order)×log₁₀(salesAmount+1)/MAX_LOG + lastEventEpochSeconds × TIEBREAKER_SCALE}</p>
 * <p>기본 가중치: view=0.1, like=0.2, order=0.7, MAX_LOG=7, TIEBREAKER_SCALE=1e-16</p>
 */
@ExtendWith(MockitoExtension.class)
class RankingScoreUpdaterTest {

    @Mock
    private RedisTemplate<String, String> writeTemplate;

    private RankingScoreUpdater updater;

    private static final long LAST_EVENT_AT = 1_712_700_000L; // 고정 epoch seconds

    @BeforeEach
    void setUp() {
        RankingProperties properties = new RankingProperties(
            new RankingProperties.Weights(0.1, 0.2, 0.7), 0.1, 0.97, 0,
            Map.of(), 0, null
        );
        updater = new RankingScoreUpdater(writeTemplate, properties);
    }

    @Nested
    @DisplayName("기본 score 계산")
    class BasicScoreCalculation {

        @Test
        @DisplayName("모든 메트릭이 0이면 주 score는 0.0 (tiebreaker만 남음)")
        void allZeros_returnsOnlyTiebreaker() {
            double score = updater.calculateScore(0, 0, 0, LAST_EVENT_AT, 0);

            assertThat(score).isCloseTo(LAST_EVENT_AT * 1e-16, within(1e-20));
        }

        @Test
        @DisplayName("view만 있을 때 score ≈ 0.1 × log₁₀(viewCount+1) / 7")
        void viewOnly() {
            double score = updater.calculateScore(99, 0, 0, LAST_EVENT_AT, 0);

            // 0.1 × log₁₀(100) / 7 = 0.2 / 7 ≈ 0.02857
            double expected = 0.1 * Math.log10(100) / 7 + LAST_EVENT_AT * 1e-16;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }

        @Test
        @DisplayName("like만 있을 때 score ≈ 0.2 × log₁₀(likeCount+1) / 7")
        void likeOnly() {
            double score = updater.calculateScore(0, 99, 0, LAST_EVENT_AT, 0);

            // 0.2 × log₁₀(100) / 7 = 0.4 / 7 ≈ 0.05714
            double expected = 0.2 * Math.log10(100) / 7 + LAST_EVENT_AT * 1e-16;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }

        @Test
        @DisplayName("order만 있을 때 score ≈ 0.7 × log₁₀(salesAmount+1) / 7")
        void orderOnly() {
            double score = updater.calculateScore(0, 0, 9999, LAST_EVENT_AT, 0);

            // 0.7 × log₁₀(10000) / 7 = 2.8 / 7 = 0.4
            double expected = 0.7 * Math.log10(10000) / 7 + LAST_EVENT_AT * 1e-16;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }
    }

    @Nested
    @DisplayName("가중치 순서 검증")
    class WeightOrdering {

        @Test
        @DisplayName("주문 1건(10000원) > 좋아요 3건 — order 가중치가 지배적")
        void order_beats_likes() {
            double scoreLikes = updater.calculateScore(0, 3, 0, LAST_EVENT_AT, 0);
            double scoreOrder = updater.calculateScore(0, 0, 10000, LAST_EVENT_AT, 0);

            assertThat(scoreOrder).isGreaterThan(scoreLikes);
        }

        @Test
        @DisplayName("좋아요 가중치 > 조회 가중치 — 같은 count일 때")
        void like_beats_view_sameCount() {
            double scoreView = updater.calculateScore(100, 0, 0, LAST_EVENT_AT, 0);
            double scoreLike = updater.calculateScore(0, 100, 0, LAST_EVENT_AT, 0);

            assertThat(scoreLike).isGreaterThan(scoreView);
        }

        @Test
        @DisplayName("복합 score: 조회 100 + 좋아요 10 + 주문 50000원")
        void compositeScore() {
            double score = updater.calculateScore(100, 10, 50000, LAST_EVENT_AT, 0);

            double expected = 0.1 * Math.log10(101) / 7
                + 0.2 * Math.log10(11) / 7
                + 0.7 * Math.log10(50001) / 7
                + LAST_EVENT_AT * 1e-16;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }
    }

    @Nested
    @DisplayName("log₁₀ 정규화 효과")
    class LogNormalization {

        @Test
        @DisplayName("view 10배 차이(100 vs 1000)가 score에서는 1.5배 미만 차이")
        void logReducesScaleDifference() {
            double score100 = updater.calculateScore(100, 0, 0, LAST_EVENT_AT, 0);
            double score1000 = updater.calculateScore(1000, 0, 0, LAST_EVENT_AT, 0);

            // tiebreaker를 제거하고 주 score만 비교
            double tiebreaker = LAST_EVENT_AT * 1e-16;
            double main100 = score100 - tiebreaker;
            double main1000 = score1000 - tiebreaker;

            assertThat(main1000).isGreaterThan(main100);
            assertThat(main1000 / main100).isLessThan(1.5);
        }

        @Test
        @DisplayName("salesAmount 100배 차이(1000 vs 100000)가 score에서 완화됨")
        void logReducesSalesAmountDominance() {
            double scoreLow = updater.calculateScore(0, 0, 1000, LAST_EVENT_AT, 0);
            double scoreHigh = updater.calculateScore(0, 0, 100000, LAST_EVENT_AT, 0);

            double tiebreaker = LAST_EVENT_AT * 1e-16;
            double mainLow = scoreLow - tiebreaker;
            double mainHigh = scoreHigh - tiebreaker;

            assertThat(mainHigh).isGreaterThan(mainLow);
            assertThat(mainHigh / mainLow).isLessThan(2.0);
        }

        @Test
        @DisplayName("0~1 정규화: 모든 가중치 합 = 1.0, 최대 메트릭에서도 주 score ≤ 1.0")
        void normalizedScore_doesNotExceedOne() {
            // MAX_LOG=7 → log₁₀(10^7) = 7, 7/7 = 1.0
            // 가중치 합 = 0.1 + 0.2 + 0.7 = 1.0
            // 모든 메트릭이 10^7-1일 때 주 score = 1.0
            double score = updater.calculateScore(9_999_999, 9_999_999, 9_999_999, 0, 0);

            assertThat(score).isLessThanOrEqualTo(1.0 + 1e-10);
        }
    }

    @Nested
    @DisplayName("음수 메트릭 방어")
    class NegativeMetricDefense {

        @Test
        @DisplayName("음수 viewCount → 0으로 클램핑되어 주 score 기여 0.0")
        void negativeViewCount_clampedToZero() {
            double score = updater.calculateScore(-5, 0, 0, LAST_EVENT_AT, 0);

            assertThat(score).isCloseTo(LAST_EVENT_AT * 1e-16, within(1e-20));
        }

        @Test
        @DisplayName("음수 likeCount → 0으로 클램핑")
        void negativeLikeCount_clampedToZero() {
            double score = updater.calculateScore(0, -10, 0, LAST_EVENT_AT, 0);

            assertThat(score).isCloseTo(LAST_EVENT_AT * 1e-16, within(1e-20));
        }

        @Test
        @DisplayName("음수 salesAmount → 0으로 클램핑")
        void negativeSalesAmount_clampedToZero() {
            double score = updater.calculateScore(0, 0, -50000, LAST_EVENT_AT, 0);

            assertThat(score).isCloseTo(LAST_EVENT_AT * 1e-16, within(1e-20));
        }

        @Test
        @DisplayName("모든 메트릭 음수 → score는 메트릭 0일 때와 동일")
        void allNegative_equalToZeroMetrics() {
            double score = updater.calculateScore(-5, -10, -50000, LAST_EVENT_AT, 0);
            double scoreZero = updater.calculateScore(0, 0, 0, LAST_EVENT_AT, 0);

            assertThat(score).isEqualTo(scoreZero);
        }

        @Test
        @DisplayName("음수 메트릭이 양수 메트릭의 score를 침범하지 않음")
        void negativeDoesNotAffectPositiveTerms() {
            double scoreWithNegative = updater.calculateScore(100, -5, 0, LAST_EVENT_AT, 0);
            double scoreViewOnly = updater.calculateScore(100, 0, 0, LAST_EVENT_AT, 0);

            assertThat(scoreWithNegative).isEqualTo(scoreViewOnly);
        }
    }

    @Nested
    @DisplayName("타이브레이커 — lastEventAt × TIEBREAKER_SCALE")
    class Tiebreaker {

        @Test
        @DisplayName("동점 시 최근 활동 상품이 상위")
        void sameMetrics_laterEvent_higherScore() {
            long earlier = 1_712_700_000L;
            long later = 1_712_700_100L;

            double scoreOld = updater.calculateScore(1, 0, 0, earlier, 0);
            double scoreNew = updater.calculateScore(1, 0, 0, later, 0);

            assertThat(scoreNew).isGreaterThan(scoreOld);
        }

        @Test
        @DisplayName("주 score가 다르면 lastEventAt이 커도 역전 불가")
        void differentMetrics_eventTimeCannotReverse() {
            long much_later = 9_999_999_999L;
            double scoreHighMetric = updater.calculateScore(2, 0, 0, 0, 0);
            double scoreLowMetric = updater.calculateScore(1, 0, 0, much_later, 0);

            assertThat(scoreHighMetric).isGreaterThan(scoreLowMetric);
        }

        @Test
        @DisplayName("TIEBREAKER_SCALE이 주 score 최소 차이보다 충분히 작음")
        void tiebreaker_doesNotExceedMinScoreDifference() {
            // epoch seconds ≈ 1.7×10⁹ → tiebreaker ≈ 1.7×10⁻⁷
            double tiebreakerMax = 2_000_000_000L * RankingScoreUpdater.TIEBREAKER_SCALE;
            // 주 score 최소 유의미 차이: view 0→1 = 0.1 × log₁₀(2) / 7 ≈ 0.0043
            double minScoreDiff = 0.1 * Math.log10(2) / 7;

            assertThat(tiebreakerMax / minScoreDiff).isLessThan(0.05);
        }

        @Test
        @DisplayName("TIEBREAKER_SCALE 상수가 1e-16")
        void scaleConstant() {
            assertThat(RankingScoreUpdater.TIEBREAKER_SCALE).isEqualTo(1e-16);
        }
    }

    @Nested
    @DisplayName("카테고리 우선순위")
    class CategoryPriority {

        @Test
        @DisplayName("categoryPriority가 정수부에 인코딩되어 score를 지배")
        void categoryPriority_dominatesScore() {
            // priority=2 vs priority=0 + 최대 메트릭(주 score ≤ 1.0)
            double scoreHighPriority = updater.calculateScore(0, 0, 0, LAST_EVENT_AT, 2);
            double scoreLowPriority = updater.calculateScore(9_999_999, 9_999_999, 9_999_999, LAST_EVENT_AT, 0);

            assertThat(scoreHighPriority).isGreaterThan(scoreLowPriority);
        }

        @Test
        @DisplayName("같은 categoryPriority 내에서는 메트릭으로 순위 결정")
        void samePriority_metricsDetermineRank() {
            double scoreLow = updater.calculateScore(10, 5, 1000, LAST_EVENT_AT, 2);
            double scoreHigh = updater.calculateScore(100, 50, 100000, LAST_EVENT_AT, 2);

            assertThat(scoreHigh).isGreaterThan(scoreLow);
        }

        @Test
        @DisplayName("categoryPriority 0 (기본) → 정수부 간섭 없음")
        void zeroPriority_noIntegerPartInterference() {
            double score = updater.calculateScore(0, 0, 0, 0, 0);

            assertThat(score).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("커스텀 가중치")
    class CustomWeights {

        @Test
        @DisplayName("가중치를 변경하면 score 비율이 달라짐")
        void differentWeights_changePriority() {
            RankingProperties viewFirst = new RankingProperties(
                new RankingProperties.Weights(0.7, 0.2, 0.1), 0.1, 0.97, 0,
                Map.of(), 0, null
            );
            RankingScoreUpdater viewUpdater = new RankingScoreUpdater(writeTemplate, viewFirst);

            double scoreView = viewUpdater.calculateScore(100, 0, 0, LAST_EVENT_AT, 0);
            double scoreOrder = viewUpdater.calculateScore(0, 0, 100, LAST_EVENT_AT, 0);

            // tiebreaker 제거 후 비교
            double tiebreaker = LAST_EVENT_AT * 1e-16;
            assertThat(scoreView - tiebreaker).isGreaterThan(scoreOrder - tiebreaker);
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
        @DisplayName("ZSET 키: 커스텀 prefix 지원")
        void zsetKey_customPrefix() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            String key = RankingScoreUpdater.zsetKey("ranking:exp:A:", date);

            assertThat(key).isEqualTo("ranking:exp:A:20260410");
        }

        @Test
        @DisplayName("주간 키: ranking:weekly:{yyyyMMdd} 형식")
        void weeklyKey_format() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            String key = RankingScoreUpdater.weeklyKey(date);

            assertThat(key).isEqualTo("ranking:weekly:20260410");
        }

        @Test
        @DisplayName("월간 키: ranking:monthly:{yyyyMMdd} 형식")
        void monthlyKey_format() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            String key = RankingScoreUpdater.monthlyKey(date);

            assertThat(key).isEqualTo("ranking:monthly:20260410");
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
        @DisplayName("ZSET TTL 상수가 8일(691200초)")
        void zsetTtlConstant_isEightDays() {
            assertThat(RankingScoreUpdater.RANKING_ZSET_TTL_SECONDS).isEqualTo(691_200L);
        }

        @Test
        @DisplayName("Hash TTL 상수가 2일(172800초)")
        void hashTtlConstant_isTwoDays() {
            assertThat(RankingScoreUpdater.RANKING_HASH_TTL_SECONDS).isEqualTo(172_800L);
        }

        @Test
        @DisplayName("집계 TTL 상수가 2일(172800초)")
        void aggregatedTtlConstant_isTwoDays() {
            assertThat(RankingScoreUpdater.RANKING_AGGREGATED_TTL_SECONDS).isEqualTo(172_800L);
        }

        @Test
        @DisplayName("MAX_LOG 상수가 7.0")
        void maxLogConstant() {
            assertThat(RankingScoreUpdater.MAX_LOG).isEqualTo(7.0);
        }
    }
}
