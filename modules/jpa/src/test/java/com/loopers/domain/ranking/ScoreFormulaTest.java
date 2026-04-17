package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScoreFormulaTest {

    private static final ScoreFormula.Weights DEFAULT_WEIGHTS = new ScoreFormula.Weights(0.1, 0.2, 0.7);
    private static final long FIXED_EPOCH = 1_712_700_000L;

    @Nested
    @DisplayName("기본 score 계산")
    class BasicScoreCalculation {

        @Test
        @DisplayName("모든 메트릭이 0이면 주 score는 0.0 (tiebreaker만 남음)")
        void allZeros_returnsOnlyTiebreaker() {
            double score = ScoreFormula.calculate(0, 0, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            assertThat(score).isCloseTo(FIXED_EPOCH * ScoreFormula.TIEBREAKER_SCALE, within(1e-20));
        }

        @Test
        @DisplayName("view만 있을 때 score ≈ 0.1 × log₁₀(viewCount+1) / 7")
        void viewOnly() {
            double score = ScoreFormula.calculate(99, 0, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            double expected = 0.1 * Math.log10(100) / 7 + FIXED_EPOCH * ScoreFormula.TIEBREAKER_SCALE;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }

        @Test
        @DisplayName("like만 있을 때 score ≈ 0.2 × log₁₀(likeCount+1) / 7")
        void likeOnly() {
            double score = ScoreFormula.calculate(0, 99, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            double expected = 0.2 * Math.log10(100) / 7 + FIXED_EPOCH * ScoreFormula.TIEBREAKER_SCALE;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }

        @Test
        @DisplayName("order만 있을 때 score ≈ 0.7 × log₁₀(salesAmount+1) / 7")
        void orderOnly() {
            double score = ScoreFormula.calculate(0, 0, 9999, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            double expected = 0.7 * Math.log10(10000) / 7 + FIXED_EPOCH * ScoreFormula.TIEBREAKER_SCALE;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }

        @Test
        @DisplayName("복합 score: 조회 100 + 좋아요 10 + 주문 50000원")
        void compositeScore() {
            double score = ScoreFormula.calculate(100, 10, 50000, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            double expected = 0.1 * Math.log10(101) / 7
                + 0.2 * Math.log10(11) / 7
                + 0.7 * Math.log10(50001) / 7
                + FIXED_EPOCH * ScoreFormula.TIEBREAKER_SCALE;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }
    }

    @Nested
    @DisplayName("음수 메트릭 방어")
    class NegativeMetricDefense {

        @Test
        @DisplayName("음수 viewCount → 0으로 클램핑되어 주 score 기여 0.0")
        void negativeViewCount_clampedToZero() {
            double score = ScoreFormula.calculate(-5, 0, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            assertThat(score).isCloseTo(FIXED_EPOCH * ScoreFormula.TIEBREAKER_SCALE, within(1e-20));
        }

        @Test
        @DisplayName("음수 likeCount → 0으로 클램핑")
        void negativeLikeCount_clampedToZero() {
            double score = ScoreFormula.calculate(0, -10, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            assertThat(score).isCloseTo(FIXED_EPOCH * ScoreFormula.TIEBREAKER_SCALE, within(1e-20));
        }

        @Test
        @DisplayName("음수 salesAmount → 0으로 클램핑")
        void negativeSalesAmount_clampedToZero() {
            double score = ScoreFormula.calculate(0, 0, -50000, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            assertThat(score).isCloseTo(FIXED_EPOCH * ScoreFormula.TIEBREAKER_SCALE, within(1e-20));
        }

        @Test
        @DisplayName("모든 메트릭 음수 → score는 메트릭 0일 때와 동일")
        void allNegative_equalToZeroMetrics() {
            double score = ScoreFormula.calculate(-5, -10, -50000, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);
            double scoreZero = ScoreFormula.calculate(0, 0, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            assertThat(score).isEqualTo(scoreZero);
        }

        @Test
        @DisplayName("음수 메트릭이 양수 메트릭의 score를 침범하지 않음")
        void negativeDoesNotAffectPositiveTerms() {
            double scoreWithNeg = ScoreFormula.calculate(100, -5, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);
            double scoreViewOnly = ScoreFormula.calculate(100, 0, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            assertThat(scoreWithNeg).isEqualTo(scoreViewOnly);
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

            double scoreOld = ScoreFormula.calculate(1, 0, 0, 0, earlier, DEFAULT_WEIGHTS);
            double scoreNew = ScoreFormula.calculate(1, 0, 0, 0, later, DEFAULT_WEIGHTS);

            assertThat(scoreNew).isGreaterThan(scoreOld);
        }

        @Test
        @DisplayName("주 score가 다르면 lastEventAt이 커도 역전 불가")
        void differentMetrics_eventTimeCannotReverse() {
            long muchLater = 9_999_999_999L;
            double scoreHighMetric = ScoreFormula.calculate(2, 0, 0, 0, 0, DEFAULT_WEIGHTS);
            double scoreLowMetric = ScoreFormula.calculate(1, 0, 0, 0, muchLater, DEFAULT_WEIGHTS);

            assertThat(scoreHighMetric).isGreaterThan(scoreLowMetric);
        }

        @Test
        @DisplayName("TIEBREAKER_SCALE이 주 score 최소 차이보다 충분히 작음")
        void tiebreaker_doesNotExceedMinScoreDifference() {
            double tiebreakerMax = 2_000_000_000L * ScoreFormula.TIEBREAKER_SCALE;
            double minScoreDiff = 0.1 * Math.log10(2) / 7;

            assertThat(tiebreakerMax / minScoreDiff).isLessThan(0.05);
        }

        @Test
        @DisplayName("TIEBREAKER_SCALE 상수가 1e-16")
        void scaleConstant() {
            assertThat(ScoreFormula.TIEBREAKER_SCALE).isEqualTo(1e-16);
        }
    }

    @Nested
    @DisplayName("카테고리 우선순위")
    class CategoryPriority {

        @Test
        @DisplayName("categoryPriority가 정수부에 인코딩되어 score를 지배")
        void categoryPriority_dominatesScore() {
            double scoreHighPriority = ScoreFormula.calculate(0, 0, 0, 2, FIXED_EPOCH, DEFAULT_WEIGHTS);
            double scoreLowPriority = ScoreFormula.calculate(9_999_999, 9_999_999, 9_999_999, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            assertThat(scoreHighPriority).isGreaterThan(scoreLowPriority);
        }

        @Test
        @DisplayName("같은 categoryPriority 내에서는 메트릭으로 순위 결정")
        void samePriority_metricsDetermineRank() {
            double scoreLow = ScoreFormula.calculate(10, 5, 1000, 2, FIXED_EPOCH, DEFAULT_WEIGHTS);
            double scoreHigh = ScoreFormula.calculate(100, 50, 100000, 2, FIXED_EPOCH, DEFAULT_WEIGHTS);

            assertThat(scoreHigh).isGreaterThan(scoreLow);
        }

        @Test
        @DisplayName("categoryPriority 0 (기본) → 정수부 간섭 없음")
        void zeroPriority_noIntegerPartInterference() {
            double score = ScoreFormula.calculate(0, 0, 0, 0, 0, DEFAULT_WEIGHTS);

            assertThat(score).isEqualTo(0.0);
        }

        @Test
        @DisplayName("categoryPriority가 정수부에 반영 — 차이가 정확히 1.0")
        void categoryPriority_addsToScore() {
            double scoreNoPriority = ScoreFormula.calculate(0, 0, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);
            double scoreWithPriority = ScoreFormula.calculate(0, 0, 0, 1, FIXED_EPOCH, DEFAULT_WEIGHTS);

            assertThat(scoreWithPriority - scoreNoPriority).isCloseTo(1.0, within(1e-10));
        }
    }

    @Nested
    @DisplayName("정규화")
    class Normalization {

        @Test
        @DisplayName("0~1 정규화: MAX_LOG에서 각 항이 1.0 상한")
        void normalizedScore_doesNotExceedOne() {
            double score = ScoreFormula.calculate(9_999_999, 9_999_999, 9_999_999, 0, 0, DEFAULT_WEIGHTS);

            assertThat(score).isLessThanOrEqualTo(1.0 + 1e-10);
        }

        @Test
        @DisplayName("MAX_LOG 상수가 7.0")
        void maxLogConstant() {
            assertThat(ScoreFormula.MAX_LOG).isEqualTo(7.0);
        }

        @Test
        @DisplayName("log 감쇄: view 10배 차이(100 vs 1000)가 score에서 1.5배 미만 차이")
        void logReducesScaleDifference() {
            double score100 = ScoreFormula.calculate(100, 0, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);
            double score1000 = ScoreFormula.calculate(1000, 0, 0, 0, FIXED_EPOCH, DEFAULT_WEIGHTS);

            double tiebreaker = FIXED_EPOCH * ScoreFormula.TIEBREAKER_SCALE;
            double main100 = score100 - tiebreaker;
            double main1000 = score1000 - tiebreaker;

            assertThat(main1000).isGreaterThan(main100);
            assertThat(main1000 / main100).isLessThan(1.5);
        }
    }

    @Nested
    @DisplayName("커스텀 가중치")
    class CustomWeights {

        @Test
        @DisplayName("가중치를 변경하면 score 비율이 달라짐")
        void differentWeights_changePriority() {
            ScoreFormula.Weights viewFirst = new ScoreFormula.Weights(0.7, 0.2, 0.1);

            double scoreView = ScoreFormula.calculate(100, 0, 0, 0, FIXED_EPOCH, viewFirst);
            double scoreOrder = ScoreFormula.calculate(0, 0, 100, 0, FIXED_EPOCH, viewFirst);

            double tiebreaker = FIXED_EPOCH * ScoreFormula.TIEBREAKER_SCALE;
            assertThat(scoreView - tiebreaker).isGreaterThan(scoreOrder - tiebreaker);
        }
    }
}
