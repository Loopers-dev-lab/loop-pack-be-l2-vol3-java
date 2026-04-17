package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankingScoreCalculatorTest {

    private final RankingScoreCalculator calculator = new RankingScoreCalculator();

    @DisplayName("viewScore는 0.1을 반환한다")
    @Test
    void shouldReturnViewWeight() {
        assertThat(calculator.viewScore()).isEqualTo(0.1);
    }

    @DisplayName("likeScore는 0.2를 반환한다")
    @Test
    void shouldReturnLikeWeight() {
        assertThat(calculator.likeScore()).isEqualTo(0.2);
    }

    @DisplayName("orderScore는 0.7 × log10(price × amount)를 반환한다")
    @Test
    void shouldCalculateOrderScore() {
        double score = calculator.orderScore(10000, 2);
        assertThat(score).isCloseTo(3.011, within(0.01));
    }

    @DisplayName("orderScore에 0 이하 금액이면 0.0을 반환한다")
    @Test
    void shouldReturnZeroForNonPositiveAmount() {
        assertThat(calculator.orderScore(0, 1)).isEqualTo(0.0);
        assertThat(calculator.orderScore(-100, 1)).isEqualTo(0.0);
    }

    @DisplayName("calculateScore는 일별 메트릭으로 총합 점수를 계산한다")
    @Test
    void shouldCalculateTotalScore() {
        double score = calculator.calculateScore(10, 5, 3);
        assertThat(score).isCloseTo(4.1, within(0.001));
    }
}
