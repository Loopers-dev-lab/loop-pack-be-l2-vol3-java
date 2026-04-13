package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.util.List;

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
            RankingScore result = calculator.calculate(new RankingEvent.View("e1", 1L));

            assertThat(result.productId()).isEqualTo(1L);
            assertThat(result.score()).isEqualTo(0.1);
        }
    }

    @DisplayName("좋아요 score를 계산할 때,")
    @Nested
    class CalculateLikeScore {

        @DisplayName("좋아요이면, 0.2를 반환한다.")
        @Test
        void returns0point2_whenLiked() {
            RankingScore result = calculator.calculate(new RankingEvent.Like("e1", 1L, true));

            assertThat(result.productId()).isEqualTo(1L);
            assertThat(result.score()).isEqualTo(0.2);
        }

        @DisplayName("좋아요 취소이면, -0.2를 반환한다.")
        @Test
        void returnsMinus0point2_whenUnliked() {
            RankingScore result = calculator.calculate(new RankingEvent.Like("e1", 1L, false));

            assertThat(result.productId()).isEqualTo(1L);
            assertThat(result.score()).isEqualTo(-0.2);
        }
    }

    @DisplayName("주문 score를 계산할 때,")
    @Nested
    class CalculateOrderScores {

        @DisplayName("0.7 × log10(price × quantity)를 반환한다.")
        @Test
        void returnsWeightTimesLog10() {
            List<RankingScore> scores = calculator.calculate(new RankingEvent.Order("e1", List.of(
                    new RankingEvent.Order.OrderItem(1L, 50000L, 1L)
            )));

            assertThat(scores).hasSize(1);
            assertThat(scores.get(0).productId()).isEqualTo(1L);
            assertThat(scores.get(0).score()).isCloseTo(3.29, offset(0.01));
        }

        @DisplayName("수량이 반영된다.")
        @Test
        void reflectsQuantity() {
            List<RankingScore> scores = calculator.calculate(new RankingEvent.Order("e1", List.of(
                    new RankingEvent.Order.OrderItem(1L, 50000L, 5L)
            )));

            assertThat(scores.get(0).score()).isCloseTo(3.78, offset(0.01));
        }

        @DisplayName("가격이 0이면, 0.0을 반환한다.")
        @Test
        void returnsZero_whenPriceIsZero() {
            List<RankingScore> scores = calculator.calculate(new RankingEvent.Order("e1", List.of(
                    new RankingEvent.Order.OrderItem(1L, 0L, 1L)
            )));

            assertThat(scores.get(0).score()).isEqualTo(0.0);
        }

        @DisplayName("수량이 0이면, 0.0을 반환한다.")
        @Test
        void returnsZero_whenQuantityIsZero() {
            List<RankingScore> scores = calculator.calculate(new RankingEvent.Order("e1", List.of(
                    new RankingEvent.Order.OrderItem(1L, 50000L, 0L)
            )));

            assertThat(scores.get(0).score()).isEqualTo(0.0);
        }

        @DisplayName("항목별로 productId 기준으로 점수를 반환한다.")
        @Test
        void returnsScoresPerProduct() {
            List<RankingScore> scores = calculator.calculate(new RankingEvent.Order("e1", List.of(
                    new RankingEvent.Order.OrderItem(1L, 50000L, 1L),
                    new RankingEvent.Order.OrderItem(2L, 30000L, 2L)
            )));

            assertThat(scores).hasSize(2);
            assertThat(scores.get(0).productId()).isEqualTo(1L);
            assertThat(scores.get(0).score()).isCloseTo(0.7 * Math.log10(50000), offset(0.001));
            assertThat(scores.get(1).productId()).isEqualTo(2L);
            assertThat(scores.get(1).score()).isCloseTo(0.7 * Math.log10(60000), offset(0.001));
        }

        @DisplayName("같은 productId의 항목이 여러 개면, 각각 반환한다.")
        @Test
        void returnsEachItemSeparately_whenSameProductId() {
            List<RankingScore> scores = calculator.calculate(new RankingEvent.Order("e1", List.of(
                    new RankingEvent.Order.OrderItem(1L, 10000L, 1L),
                    new RankingEvent.Order.OrderItem(1L, 20000L, 2L)
            )));

            assertThat(scores).hasSize(2);
            assertThat(scores.get(0).score()).isCloseTo(0.7 * Math.log10(10000), offset(0.001));
            assertThat(scores.get(1).score()).isCloseTo(0.7 * Math.log10(40000), offset(0.001));
        }
    }

    @DisplayName("가중치 비교 시,")
    @Nested
    class WeightComparison {

        @DisplayName("주문 1건(50,000원) 점수가 좋아요 3건 점수보다 크다.")
        @Test
        void singleOrderScoreExceedsThreeLikes() {
            // arrange
            double orderScore = calculator.calculate(new RankingEvent.Order("e1", List.of(
                    new RankingEvent.Order.OrderItem(1L, 50000L, 1L)
            ))).get(0).score();
            double threeLikesScore = calculator.calculate(new RankingEvent.Like("e2", 1L, true)).score() * 3;

            // assert
            assertThat(orderScore).isGreaterThan(threeLikesScore);
        }
    }
}
