package com.loopers.domain.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMetricsTest {

    @DisplayName("create: productId로 초기 상태의 ProductMetrics를 생성한다")
    @Test
    void create() {
        ProductMetrics metrics = ProductMetrics.create(10L);

        assertThat(metrics.getProductId()).isEqualTo(10L);
        assertThat(metrics.getViewCount()).isZero();
        assertThat(metrics.getLikeCount()).isZero();
        assertThat(metrics.getSalesCount()).isZero();
    }
}
