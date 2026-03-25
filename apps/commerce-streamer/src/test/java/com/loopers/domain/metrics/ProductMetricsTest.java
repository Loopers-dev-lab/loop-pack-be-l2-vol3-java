package com.loopers.domain.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductMetricsTest {

    @DisplayName("ProductMetrics를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("productId가 설정되고, 모든 지표가 0으로 초기화된다.")
        @Test
        void initializesWithZeroCounts() {
            // act
            ProductMetrics metrics = ProductMetrics.create(1L);

            // assert
            assertAll(
                    () -> assertThat(metrics.getProductId()).isEqualTo(1L),
                    () -> assertThat(metrics.getLikeCount()).isZero(),
                    () -> assertThat(metrics.getOrderCount()).isZero(),
                    () -> assertThat(metrics.getViewCount()).isZero()
            );
        }
    }

    @DisplayName("좋아요 수를 증가시킬 때,")
    @Nested
    class IncrementLikeCount {

        @DisplayName("likeCount가 1 증가한다.")
        @Test
        void incrementsByOne() {
            // arrange
            ProductMetrics metrics = ProductMetrics.create(1L);

            // act
            metrics.incrementLikeCount();

            // assert
            assertThat(metrics.getLikeCount()).isEqualTo(1L);
        }
    }

    @DisplayName("좋아요 수를 감소시킬 때,")
    @Nested
    class DecrementLikeCount {

        @DisplayName("likeCount가 1 감소한다.")
        @Test
        void decrementsByOne() {
            // arrange
            ProductMetrics metrics = ProductMetrics.create(1L);
            metrics.incrementLikeCount();
            metrics.incrementLikeCount();

            // act
            metrics.decrementLikeCount();

            // assert
            assertThat(metrics.getLikeCount()).isEqualTo(1L);
        }

        @DisplayName("likeCount가 0이면, 0을 유지한다.")
        @Test
        void staysAtZero_whenAlreadyZero() {
            // arrange
            ProductMetrics metrics = ProductMetrics.create(1L);

            // act
            metrics.decrementLikeCount();

            // assert
            assertThat(metrics.getLikeCount()).isZero();
        }
    }

    @DisplayName("주문 수량을 추가할 때,")
    @Nested
    class AddOrderCount {

        @DisplayName("주어진 수량만큼 orderCount가 증가한다.")
        @Test
        void addsQuantity() {
            // arrange
            ProductMetrics metrics = ProductMetrics.create(1L);

            // act
            metrics.addOrderCount(3L);

            // assert
            assertThat(metrics.getOrderCount()).isEqualTo(3L);
        }

        @DisplayName("여러 번 호출하면 누적된다.")
        @Test
        void accumulates() {
            // arrange
            ProductMetrics metrics = ProductMetrics.create(1L);

            // act
            metrics.addOrderCount(2L);
            metrics.addOrderCount(5L);

            // assert
            assertThat(metrics.getOrderCount()).isEqualTo(7L);
        }
    }

    @DisplayName("조회 수를 증가시킬 때,")
    @Nested
    class IncrementViewCount {

        @DisplayName("viewCount가 1 증가한다.")
        @Test
        void incrementsByOne() {
            // arrange
            ProductMetrics metrics = ProductMetrics.create(1L);

            // act
            metrics.incrementViewCount();

            // assert
            assertThat(metrics.getViewCount()).isEqualTo(1L);
        }
    }
}
