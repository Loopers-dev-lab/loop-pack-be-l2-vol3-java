package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductDailyMetrics;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.infrastructure.metrics.ProductDailyMetricsJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("ProductMetricsAppService 배치 처리 테스트")
class ProductMetricsAppServiceBatchTest {

    @Autowired
    private ProductMetricsAppService productMetricsAppService;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private ProductDailyMetricsJpaRepository productDailyMetricsJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("catalog 배치: 같은 상품의 view/like 이벤트가 집계되어 한 번에 반영된다")
    void catalogBatch_aggregatesPerProduct() {
        ZonedDateTime now = ZonedDateTime.now();
        LocalDate today = now.toLocalDate();

        List<CatalogMetricEvent> events = List.of(
                new CatalogMetricEvent("catalog-events:0:1", CatalogMetricEvent.Type.VIEWED, 1001L, false, now),
                new CatalogMetricEvent("catalog-events:0:2", CatalogMetricEvent.Type.VIEWED, 1001L, false, now),
                new CatalogMetricEvent("catalog-events:0:3", CatalogMetricEvent.Type.VIEWED, 1001L, false, now),
                new CatalogMetricEvent("catalog-events:0:4", CatalogMetricEvent.Type.LIKED, 1001L, true, now),
                new CatalogMetricEvent("catalog-events:0:5", CatalogMetricEvent.Type.VIEWED, 1002L, false, now),
                new CatalogMetricEvent("catalog-events:0:6", CatalogMetricEvent.Type.LIKED, 1002L, true, now),
                new CatalogMetricEvent("catalog-events:0:7", CatalogMetricEvent.Type.LIKED, 1002L, false, now)
        );

        productMetricsAppService.handleCatalogEventBatch(events);

        ProductMetrics m1 = productMetricsJpaRepository.findById(1001L).orElseThrow();
        assertThat(m1.getViewCount()).isEqualTo(3);
        assertThat(m1.getLikeCount()).isEqualTo(1);

        ProductMetrics m2 = productMetricsJpaRepository.findById(1002L).orElseThrow();
        assertThat(m2.getViewCount()).isEqualTo(1);
        assertThat(m2.getLikeCount()).isZero();

        List<ProductDailyMetrics> dailies = productDailyMetricsJpaRepository.findByMetricDate(today);
        assertThat(dailies).hasSize(2);
    }

    @Test
    @DisplayName("catalog 배치: 이미 처리된 eventId 는 스킵된다 (멱등성)")
    void catalogBatch_idempotent() {
        ZonedDateTime now = ZonedDateTime.now();

        List<CatalogMetricEvent> events = List.of(
                new CatalogMetricEvent("catalog-events:0:100", CatalogMetricEvent.Type.VIEWED, 2001L, false, now),
                new CatalogMetricEvent("catalog-events:0:101", CatalogMetricEvent.Type.VIEWED, 2001L, false, now)
        );

        productMetricsAppService.handleCatalogEventBatch(events);
        productMetricsAppService.handleCatalogEventBatch(events);

        ProductMetrics m = productMetricsJpaRepository.findById(2001L).orElseThrow();
        assertThat(m.getViewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("order 배치: 생성/취소가 섞인 배치도 productId 별 집계가 맞는다")
    void orderBatch_createAndCancelMix() {
        ZonedDateTime now = ZonedDateTime.now();
        LocalDate today = now.toLocalDate();

        List<OrderMetricEvent> events = List.of(
                new OrderMetricEvent("order-events:0:1", OrderMetricEvent.Type.CREATED,
                        List.of(3001L, 3002L), 20000L, now),
                new OrderMetricEvent("order-events:0:2", OrderMetricEvent.Type.CREATED,
                        List.of(3001L), 5000L, now),
                new OrderMetricEvent("order-events:0:3", OrderMetricEvent.Type.CANCELED,
                        List.of(3002L), 10000L, now)
        );

        productMetricsAppService.handleOrderEventBatch(events);

        ProductMetrics m1 = productMetricsJpaRepository.findById(3001L).orElseThrow();
        assertThat(m1.getSalesCount()).isEqualTo(2);

        ProductMetrics m2 = productMetricsJpaRepository.findById(3002L).orElseThrow();
        assertThat(m2.getSalesCount()).isZero(); // +1 -1 = 0

        ProductDailyMetrics d1 = productDailyMetricsJpaRepository.findByMetricDate(today).stream()
                .filter(d -> d.getProductId().equals(3001L))
                .findFirst().orElseThrow();
        // 20000/2 = 10000 + 5000/1 = 5000 → 15000
        assertThat(d1.getOrderAmount()).isEqualTo(15000);

        ProductDailyMetrics d2 = productDailyMetricsJpaRepository.findByMetricDate(today).stream()
                .filter(d -> d.getProductId().equals(3002L))
                .findFirst().orElseThrow();
        // 20000/2 = 10000, 취소 10000/1 = -10000 → 0
        assertThat(d2.getOrderAmount()).isZero();
    }

    @Test
    @DisplayName("빈 배치는 아무 동작도 하지 않는다")
    void emptyBatch_noop() {
        productMetricsAppService.handleCatalogEventBatch(List.of());
        productMetricsAppService.handleOrderEventBatch(List.of());

        assertThat(productMetricsJpaRepository.findAll()).isEmpty();
    }
}
