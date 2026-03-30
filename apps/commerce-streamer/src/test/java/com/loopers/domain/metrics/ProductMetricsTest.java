package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsTest {

    @DisplayName("applyLike() 를 호출할 때, ")
    @Nested
    class ApplyLike {

        @DisplayName("delta 가 1 이면, likeCount 가 증가한다.")
        @Test
        void increasesLikeCount_whenDeltaIsPositive() {
            // arrange
            ProductMetrics metrics = ProductMetrics.of(1L);

            // act
            metrics.applyLike(1);

            // assert
            assertThat(metrics.likeCount()).isEqualTo(1L);
        }

        @DisplayName("delta 가 -1 이면, likeCount 가 감소한다.")
        @Test
        void decreasesLikeCount_whenDeltaIsNegative() {
            // arrange
            ProductMetrics metrics = ProductMetrics.of(1L);
            metrics.applyLike(1);

            // act
            metrics.applyLike(-1);

            // assert
            assertThat(metrics.likeCount()).isEqualTo(0L);
        }
    }

    @DisplayName("applyOrder() 를 호출할 때, ")
    @Nested
    class ApplyOrder {

        @DisplayName("quantity 만큼 orderCount 가 증가한다.")
        @Test
        void increasesOrderCount_byQuantity() {
            // arrange
            ProductMetrics metrics = ProductMetrics.of(1L);

            // act
            metrics.applyOrder(3L);

            // assert
            assertThat(metrics.orderCount()).isEqualTo(3L);
        }
    }
}
