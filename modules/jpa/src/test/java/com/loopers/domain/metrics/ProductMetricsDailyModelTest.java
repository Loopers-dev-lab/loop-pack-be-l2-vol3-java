package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class ProductMetricsDailyModelTest {

    @DisplayName("ProductMetricsDaily 생성 시 카운트는 0으로 초기화된다")
    @Test
    void shouldInitializeWithZeroCounts() {
        Long productId = 1L;
        LocalDate date = LocalDate.of(2026, 4, 14);

        var metrics = new ProductMetricsDaily(productId, date);

        assertAll(
            () -> assertThat(metrics.getProductId()).isEqualTo(productId),
            () -> assertThat(metrics.getMetricsDate()).isEqualTo(date),
            () -> assertThat(metrics.getLikeCount()).isZero(),
            () -> assertThat(metrics.getViewCount()).isZero(),
            () -> assertThat(metrics.getSaleCount()).isZero()
        );
    }

    @DisplayName("incrementLikeCount 호출 시 likeCount가 1 증가한다")
    @Test
    void shouldIncrementLikeCount() {
        var metrics = new ProductMetricsDaily(1L, LocalDate.now());
        metrics.incrementLikeCount();
        assertThat(metrics.getLikeCount()).isEqualTo(1);
    }

    @DisplayName("decrementLikeCount 호출 시 likeCount가 1 감소하되 0 미만으로 내려가지 않는다")
    @Test
    void shouldDecrementLikeCountWithFloor() {
        var metrics = new ProductMetricsDaily(1L, LocalDate.now());
        metrics.decrementLikeCount();
        assertThat(metrics.getLikeCount()).isZero();

        metrics.incrementLikeCount();
        metrics.incrementLikeCount();
        metrics.decrementLikeCount();
        assertThat(metrics.getLikeCount()).isEqualTo(1);
    }

    @DisplayName("incrementViewCount 호출 시 viewCount가 1 증가한다")
    @Test
    void shouldIncrementViewCount() {
        var metrics = new ProductMetricsDaily(1L, LocalDate.now());
        metrics.incrementViewCount();
        assertThat(metrics.getViewCount()).isEqualTo(1);
    }

    @DisplayName("incrementSaleCount 호출 시 saleCount가 quantity만큼 증가한다")
    @Test
    void shouldIncrementSaleCountByQuantity() {
        var metrics = new ProductMetricsDaily(1L, LocalDate.now());
        metrics.incrementSaleCount(3);
        assertThat(metrics.getSaleCount()).isEqualTo(3);
    }
}
