package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductDailyMetricsRepository;
import com.loopers.domain.metrics.ProductDailyMetricsRepository.DailyDelta;
import com.loopers.domain.metrics.ProductDailyMetrics;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("ProductDailyMetricsJpaRepository 통합 테스트")
class ProductDailyMetricsJpaRepositoryTest {

    @Autowired
    private ProductDailyMetricsRepository productDailyMetricsRepository;

    @Autowired
    private ProductDailyMetricsJpaRepository productDailyMetricsJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("첫 unlike 이벤트는 like_count를 0으로 저장한다")
    void upsertLikeCount_firstNegativeDelta_isFlooredToZero() {
        LocalDate today = LocalDate.now();
        ZonedDateTime now = ZonedDateTime.now();

        productDailyMetricsRepository.upsertLikeCount(101L, today, -1, now);

        ProductDailyMetrics metrics = productDailyMetricsJpaRepository.findByMetricDate(today).getFirst();
        assertThat(metrics.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("첫 음수 주문 금액 이벤트는 order_amount를 0으로 저장한다")
    void upsertOrderAmount_firstNegativeAmount_isFlooredToZero() {
        LocalDate today = LocalDate.now();
        ZonedDateTime now = ZonedDateTime.now();

        productDailyMetricsRepository.upsertOrderAmount(101L, today, -10_000, now);

        ProductDailyMetrics metrics = productDailyMetricsJpaRepository.findByMetricDate(today).getFirst();
        assertThat(metrics.getOrderAmount()).isZero();
    }

    @Test
    @DisplayName("bulkUpsert: 서로 다른 상품의 view/like/order 델타가 정확히 반영된다")
    void bulkUpsert_mixedDeltas() {
        LocalDate today = LocalDate.now();
        ZonedDateTime now = ZonedDateTime.now();

        List<DailyDelta> deltas = List.of(
                new DailyDelta(201L, today, 3, 1, 0, now),
                new DailyDelta(202L, today, 0, 0, 5000, now),
                new DailyDelta(203L, today, 1, 2, 1000, now)
        );

        productDailyMetricsRepository.bulkUpsert(deltas);

        Map<Long, ProductDailyMetrics> byId = productDailyMetricsJpaRepository.findByMetricDate(today).stream()
                .collect(Collectors.toMap(ProductDailyMetrics::getProductId, m -> m));

        assertThat(byId.get(201L).getViewCount()).isEqualTo(3);
        assertThat(byId.get(201L).getLikeCount()).isEqualTo(1);
        assertThat(byId.get(202L).getOrderAmount()).isEqualTo(5000);
        assertThat(byId.get(203L).getViewCount()).isEqualTo(1);
        assertThat(byId.get(203L).getLikeCount()).isEqualTo(2);
        assertThat(byId.get(203L).getOrderAmount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("bulkUpsert: 기존 row 가 있을 때 델타가 누적된다")
    void bulkUpsert_accumulates() {
        LocalDate today = LocalDate.now();
        ZonedDateTime now = ZonedDateTime.now();

        productDailyMetricsRepository.bulkUpsert(List.of(
                new DailyDelta(301L, today, 10, 5, 1000, now)
        ));
        productDailyMetricsRepository.bulkUpsert(List.of(
                new DailyDelta(301L, today, 2, -1, 500, now)
        ));

        ProductDailyMetrics m = productDailyMetricsJpaRepository.findByMetricDate(today).getFirst();
        assertThat(m.getViewCount()).isEqualTo(12);
        assertThat(m.getLikeCount()).isEqualTo(4);
        assertThat(m.getOrderAmount()).isEqualTo(1500);
    }

    @Test
    @DisplayName("bulkUpsert: 음수 델타가 누적 결과를 음수로 만들지 않는다 (GREATEST 방어)")
    void bulkUpsert_negativeClamped() {
        LocalDate today = LocalDate.now();
        ZonedDateTime now = ZonedDateTime.now();

        productDailyMetricsRepository.bulkUpsert(List.of(
                new DailyDelta(401L, today, 0, 1, 100, now)
        ));
        productDailyMetricsRepository.bulkUpsert(List.of(
                new DailyDelta(401L, today, 0, -10, -9999, now)
        ));

        ProductDailyMetrics m = productDailyMetricsJpaRepository.findByMetricDate(today).getFirst();
        assertThat(m.getLikeCount()).isZero();
        assertThat(m.getOrderAmount()).isZero();
    }
}
