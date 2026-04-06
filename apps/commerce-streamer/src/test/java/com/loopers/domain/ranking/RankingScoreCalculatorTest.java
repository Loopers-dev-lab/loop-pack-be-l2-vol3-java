package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RankingScoreCalculatorTest {

    private final RankingScoreCalculator calculator = new RankingScoreCalculator();

    @DisplayName("조회 score를 계산할 때,")
    @Nested
    class CalculateViewScore {

        @DisplayName("0.1을 반환한다.")
        @Test
        void returns0point1() {
            assertThat(calculator.calculateViewScore()).isEqualTo(0.1);
        }
    }

    @DisplayName("좋아요 score를 계산할 때,")
    @Nested
    class CalculateLikeScore {

        @DisplayName("좋아요이면, 0.2를 반환한다.")
        @Test
        void returns0point2_whenLiked() {
            assertThat(calculator.calculateLikeScore(true)).isEqualTo(0.2);
        }

        @DisplayName("좋아요 취소이면, -0.2를 반환한다.")
        @Test
        void returnsMinus0point2_whenUnliked() {
            assertThat(calculator.calculateLikeScore(false)).isEqualTo(-0.2);
        }
    }

    @DisplayName("주문 score를 계산할 때,")
    @Nested
    class CalculateOrderScore {

        @DisplayName("0.7 × log10(price × quantity)를 반환한다.")
        @Test
        void returnsWeightTimesLog10() {
            double score = calculator.calculateOrderScore(50000L, 1L);

            assertThat(score).isCloseTo(3.29, org.assertj.core.data.Offset.offset(0.01));
        }

        @DisplayName("수량이 반영된다.")
        @Test
        void reflectsQuantity() {
            double score = calculator.calculateOrderScore(50000L, 5L);

            assertThat(score).isCloseTo(3.78, org.assertj.core.data.Offset.offset(0.01));
        }

        @DisplayName("가격이 0이면, 0.0을 반환한다.")
        @Test
        void returnsZero_whenPriceIsZero() {
            assertThat(calculator.calculateOrderScore(0L, 1L)).isEqualTo(0.0);
        }

        @DisplayName("수량이 0이면, 0.0을 반환한다.")
        @Test
        void returnsZero_whenQuantityIsZero() {
            assertThat(calculator.calculateOrderScore(50000L, 0L)).isEqualTo(0.0);
        }
    }
}
