package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsTest {

    @DisplayName("create: productId와 metricDate로 초기 상태의 ProductMetrics를 생성한다")
    @Test
    void create() {
        LocalDate date = LocalDate.of(2026, 4, 12);
        ProductMetrics metrics = ProductMetrics.create(10L, date);

        assertThat(metrics.getProductId()).isEqualTo(10L);
        assertThat(metrics.getMetricDate()).isEqualTo(date);
        assertThat(metrics.getViewCount()).isZero();
        assertThat(metrics.getLikeCount()).isZero();
        assertThat(metrics.getSalesCount()).isZero();
    }
}
