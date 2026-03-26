package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductMetrics 단위 테스트")
class ProductMetricsTest {

    @Nested
    @DisplayName("create - ProductMetrics 생성")
    class Create {

        @Test
        @DisplayName("성공: 생성 시 모든 카운트는 0이다")
        void create_allCountsAreZero() {
            // When
            ProductMetrics metrics = ProductMetrics.create(100L);

            // Then
            assertThat(metrics.getProductId()).isEqualTo(100L);
            assertThat(metrics.getLikesCount()).isZero();
            assertThat(metrics.getViewCount()).isZero();
            assertThat(metrics.getOrderCount()).isZero();
        }
    }

    @Nested
    @DisplayName("increaseLikes - 좋아요 증가")
    class IncreaseLikes {

        @Test
        @DisplayName("성공: likesCount가 1 증가한다")
        void increaseLikes_incrementsByOne() {
            // Given
            ProductMetrics metrics = ProductMetrics.create(100L);

            // When
            metrics.increaseLikes();

            // Then
            assertThat(metrics.getLikesCount()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("decreaseLikes - 좋아요 감소")
    class DecreaseLikes {

        @Test
        @DisplayName("성공: likesCount가 1 감소한다")
        void decreaseLikes_decrementsByOne() {
            // Given
            ProductMetrics metrics = ProductMetrics.create(100L);
            metrics.increaseLikes();
            metrics.increaseLikes();

            // When
            metrics.decreaseLikes();

            // Then
            assertThat(metrics.getLikesCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("성공: likesCount가 0이면 감소하지 않는다")
        void decreaseLikes_doesNotGoBelowZero() {
            // Given
            ProductMetrics metrics = ProductMetrics.create(100L);

            // When
            metrics.decreaseLikes();

            // Then
            assertThat(metrics.getLikesCount()).isZero();
        }
    }
}
