package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.RankingScoreCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingScoreCalculatorTest {

    private final RankingScoreCalculator calculator = new RankingScoreCalculator();

    @DisplayName("viewCount * 0.1 + likeCount * 0.2 + salesQuantity * 0.6 으로 점수를 계산한다")
    @Test
    void calculatesScoreWithWeights() {
        // arrange
        long viewCount = 1000L;
        long likeCount = 50L;
        long salesQuantity = 10L;

        // act
        double score = calculator.calculate(viewCount, likeCount, salesQuantity);

        // assert - 1000*0.1 + 50*0.2 + 10*0.6 = 100 + 10 + 6 = 116
        assertThat(score).isEqualTo(116.0);
    }

    @DisplayName("모든 값이 0이면 점수는 0이다")
    @Test
    void returnsZeroForZeroInputs() {
        assertThat(calculator.calculate(0L, 0L, 0L)).isEqualTo(0.0);
    }
}
