package com.loopers.domain.rank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RankScoreCalculatorTest {

    private final RankScoreCalculator calculator = new RankScoreCalculator(
            new RankingWeightProperties(0.1, 0.2, 0.7)
    );

    @DisplayName("기본 가중치로 점수를 계산한다")
    @Test
    void calculate_withDefaultWeights() {
        // act
        double score = calculator.calculate(100, 50, BigDecimal.valueOf(1000));

        // assert — 0.1*100 + 0.2*50 + 0.7*1000 = 10 + 10 + 700 = 720.0
        assertThat(score).isCloseTo(720.0, within(0.001));
    }

    @DisplayName("모든 값이 0이면 score는 0이다")
    @Test
    void calculate_allZero() {
        double score = calculator.calculate(0, 0, BigDecimal.ZERO);

        assertThat(score).isEqualTo(0.0);
    }

    @DisplayName("BigDecimal 소수점 정밀도가 유지된다")
    @Test
    void calculate_decimalPrecision() {
        double score = calculator.calculate(0, 0, BigDecimal.valueOf(99.99));

        // 0.7 * 99.99 = 69.993
        assertThat(score).isCloseTo(69.993, within(0.001));
    }

    @DisplayName("가중치 변경 시 결과가 달라진다")
    @Test
    void calculate_withDifferentWeights() {
        RankScoreCalculator customCalculator = new RankScoreCalculator(
                new RankingWeightProperties(0.5, 0.3, 0.2)
        );

        double score = customCalculator.calculate(100, 50, BigDecimal.valueOf(1000));

        // 0.5*100 + 0.3*50 + 0.2*1000 = 50 + 15 + 200 = 265.0
        assertThat(score).isCloseTo(265.0, within(0.001));
    }
}
