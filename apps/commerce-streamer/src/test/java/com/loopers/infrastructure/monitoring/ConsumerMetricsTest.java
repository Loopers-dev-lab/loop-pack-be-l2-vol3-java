package com.loopers.infrastructure.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConsumerMetrics 단위 테스트")
class ConsumerMetricsTest {

    private SimpleMeterRegistry registry;
    private ConsumerMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ConsumerMetrics(registry);
        metrics.init();
    }

    @Test
    @DisplayName("catalog 처리/SKIP/실패 카운터")
    void catalogCounters_ShouldIncrement() {
        metrics.recordCatalogProcessed();
        metrics.recordCatalogProcessed();
        metrics.recordCatalogSkipped();
        metrics.recordCatalogFailed();

        assertThat(registry.counter("consumer.catalog.processed").count()).isEqualTo(2.0);
        assertThat(registry.counter("consumer.catalog.skipped").count()).isEqualTo(1.0);
        assertThat(registry.counter("consumer.catalog.failed").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("order 처리/SKIP 카운터")
    void orderCounters_ShouldIncrement() {
        metrics.recordOrderProcessed();
        metrics.recordOrderSkipped();

        assertThat(registry.counter("consumer.order.processed").count()).isEqualTo(1.0);
        assertThat(registry.counter("consumer.order.skipped").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("쿠폰 발급/거절 카운터")
    void couponCounters_ShouldIncrement() {
        metrics.recordCouponIssued();
        metrics.recordCouponRejected();
        metrics.recordCouponRejected();

        assertThat(registry.counter("consumer.coupon.issued").count()).isEqualTo(1.0);
        assertThat(registry.counter("consumer.coupon.rejected").count()).isEqualTo(2.0);
    }
}
