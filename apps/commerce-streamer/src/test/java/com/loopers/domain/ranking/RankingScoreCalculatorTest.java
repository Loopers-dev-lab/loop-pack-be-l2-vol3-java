package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingScoreCalculatorTest {

    private final RankingScoreCalculator calculator = new RankingScoreCalculator(RankingScoreWeights.questExample());

    @Test
    @DisplayName("Quest 예시 가중치에서 판매 건수 2가 좋아요 3보다 총점이 크다")
    void calculate_whenSoldTwoAndThreeLikes_soldScoreGreaterThanLikes() {
        double likesOnly = calculator.calculate(new RankingMetricCounts(0L, 3L, 0L));
        double soldOnly = calculator.calculate(new RankingMetricCounts(0L, 0L, 2L));

        assertThat(soldOnly).isGreaterThan(likesOnly);
    }

    @Test
    @DisplayName("모든 카운트가 0이면 총점은 0이다")
    void calculate_whenAllCountsZero_returnsZero() {
        assertThat(calculator.calculate(new RankingMetricCounts(0L, 0L, 0L))).isZero();
    }

    @Test
    @DisplayName("조회·좋아요 집계가 Quest 예시 가중치로 반영된다")
    void calculate_whenViewsAndLikes_matchesFormula() {
        assertThat(calculator.calculate(new RankingMetricCounts(100L, 2L, 0L))).isEqualTo(10.4d);
    }

    @Test
    @DisplayName("음수 카운트는 허용하지 않는다")
    void metricCounts_whenNegative_throws() {
        assertThatThrownBy(() -> new RankingMetricCounts(-1L, 0L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("음수 가중치는 허용하지 않는다")
    void weights_whenNegative_throws() {
        assertThatThrownBy(() -> new RankingScoreWeights(-0.1d, 0.2d, 0.6d))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
