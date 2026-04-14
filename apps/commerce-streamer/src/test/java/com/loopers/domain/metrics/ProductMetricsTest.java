package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsTest {

    @DisplayName("applyLike() 를 호출할 때, ")
    @Nested
    class ApplyLike {

        @DisplayName("delta 가 1 이면, likeCount 가 증가한다.")
        @Test
        void increasesLikeCount_whenDeltaIsPositive() {
            // arrange
            ProductMetrics metrics = ProductMetrics.of(1L, LocalDateTime.of(2026, 4, 8, 10, 0, 0));

            // act
            metrics.applyLike(1);

            // assert
            assertThat(metrics.likeCount()).isEqualTo(1L);
        }

        @DisplayName("delta 가 -1 이면, likeCount 가 감소한다.")
        @Test
        void decreasesLikeCount_whenDeltaIsNegative() {
            // arrange
            ProductMetrics metrics = ProductMetrics.of(1L, LocalDateTime.of(2026, 4, 8, 10, 0, 0));
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

        @DisplayName("quantity 만큼 orderCount 가 증가하고, salesAmount 가 반영된다.")
        @Test
        void increasesOrderCountAndSalesAmount() {
            // arrange
            ProductMetrics metrics = ProductMetrics.of(1L, LocalDateTime.of(2026, 4, 8, 10, 0, 0));

            // act
            metrics.applyOrder(3L, 15000L);

            // assert
            assertThat(metrics.orderCount()).isEqualTo(3L);
            assertThat(metrics.salesAmount()).isEqualTo(15000L);
        }
    }
}
