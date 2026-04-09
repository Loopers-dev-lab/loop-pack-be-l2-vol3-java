package com.loopers.domain.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("ScoreAggregator 단위 테스트")
class ScoreAggregatorTest {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;
    private static final double TOLERANCE = 0.0001;

    private ScoreAggregator aggregator;

    @BeforeEach
    void setUp() {
        RankingWeightProperties weights = new RankingWeightProperties(VIEW_WEIGHT, LIKE_WEIGHT, ORDER_WEIGHT);
        aggregator = new ScoreAggregator(weights);
    }

    @Nested
    @DisplayName("개별 신호 점수 계산")
    class IndividualScore {

        @Test
        @DisplayName("View 1건의 점수는 view 가중치와 같다")
        void scoreForView() {
            double score = aggregator.scoreForView();

            assertThat(score).isCloseTo(VIEW_WEIGHT, within(TOLERANCE));
        }

        @Test
        @DisplayName("Like delta=1의 점수는 like 가중치와 같다")
        void scoreForLike() {
            double score = aggregator.scoreForLike(1);

            assertThat(score).isCloseTo(LIKE_WEIGHT, within(TOLERANCE));
        }

        @Test
        @DisplayName("Like delta=3의 점수는 like 가중치 × 3이다")
        void scoreForLikeMultiple() {
            double score = aggregator.scoreForLike(3);

            assertThat(score).isCloseTo(LIKE_WEIGHT * 3, within(TOLERANCE));
        }

        @Test
        @DisplayName("Order 점수는 order 가중치 × price × quantity다")
        void scoreForOrder() {
            double score = aggregator.scoreForOrder(BigDecimal.valueOf(10000), 2);

            assertThat(score).isCloseTo(ORDER_WEIGHT * 10000 * 2, within(TOLERANCE));
        }
    }

    @Nested
    @DisplayName("전체 신호 기반 합산 (재집계용)")
    class CalculateTotal {

        @Test
        @DisplayName("모든 신호를 가중치 합산한다")
        void calculatesTotalScore() {
            double score = aggregator.calculateTotal(150, 30, 50000);

            double expected = VIEW_WEIGHT * 150 + LIKE_WEIGHT * 30 + ORDER_WEIGHT * 50000;
            assertThat(score).isCloseTo(expected, within(TOLERANCE));
        }

        @Test
        @DisplayName("모든 카운트가 0이면 점수도 0이다")
        void zeroCountsReturnZero() {
            double score = aggregator.calculateTotal(0, 0, 0);

            assertThat(score).isEqualTo(0.0);
        }

        @Test
        @DisplayName("가중치 변경 시 동일한 원본에서 다른 점수가 산출된다")
        void differentWeightsProduceDifferentScores() {
            RankingWeightProperties newWeights = new RankingWeightProperties(0.3, 0.2, 0.5);
            ScoreAggregator newAggregator = new ScoreAggregator(newWeights);

            double originalScore = aggregator.calculateTotal(100, 50, 10000);
            double newScore = newAggregator.calculateTotal(100, 50, 10000);

            assertThat(originalScore).isNotEqualTo(newScore);

            double expectedOriginal = 0.1 * 100 + 0.2 * 50 + 0.7 * 10000;
            double expectedNew = 0.3 * 100 + 0.2 * 50 + 0.5 * 10000;
            assertThat(originalScore).isCloseTo(expectedOriginal, within(TOLERANCE));
            assertThat(newScore).isCloseTo(expectedNew, within(TOLERANCE));
        }

        @Test
        @DisplayName("대량 카운트에서도 정상 계산된다")
        void handlesLargeValues() {
            double score = aggregator.calculateTotal(1_000_000, 100_000, 999_999_999.99);

            double expected = VIEW_WEIGHT * 1_000_000 + LIKE_WEIGHT * 100_000 + ORDER_WEIGHT * 999_999_999.99;
            assertThat(score).isCloseTo(expected, within(1.0));
        }
    }
}
