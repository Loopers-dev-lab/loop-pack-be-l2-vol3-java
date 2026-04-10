package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RankingScoreCalculatorTest {

    private final RankingScoreCalculator calculator = new RankingScoreCalculator();

    @DisplayName("조회 이벤트 점수 계산")
    @Nested
    class ViewScore {
        @Test
        void returns_weight_times_1() {
            double score = calculator.viewScore();
            assertThat(score).isEqualTo(0.1);
        }
    }

    @DisplayName("좋아요 이벤트 점수 계산")
    @Nested
    class LikeScore {
        @Test
        void returns_weight_times_1() {
            double score = calculator.likeScore();
            assertThat(score).isEqualTo(0.2);
        }
    }

    @DisplayName("주문 이벤트 점수 계산")
    @Nested
    class OrderScore {
        @Test
        void applies_log_normalization() {
            // price=10000, amount=1 → log10(10000) = 4.0 → 0.7 * 4.0 = 2.8
            double score = calculator.orderScore(10000, 1);
            assertThat(score).isCloseTo(2.8, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        void handles_minimum_order() {
            // price=1000, amount=1 → log10(1000) = 3.0 → 0.7 * 3.0 = 2.1
            double score = calculator.orderScore(1000, 1);
            assertThat(score).isCloseTo(2.1, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        void handles_zero_amount_gracefully() {
            double score = calculator.orderScore(1000, 0);
            assertThat(score).isEqualTo(0.0);
        }
    }
}
