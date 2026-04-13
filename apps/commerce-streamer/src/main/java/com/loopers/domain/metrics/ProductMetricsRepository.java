package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsRepository {

    Optional<ProductMetrics> findByProductIdAndMetricsDate(Long productId, LocalDate metricsDate);

    ProductMetrics save(ProductMetrics metrics);

    void incrementLikeCount(Long productId, LocalDate metricsDate);

    void decrementLikeCount(Long productId, LocalDate metricsDate);

    void incrementViewCount(Long productId, LocalDate metricsDate);

    void incrementSalesCount(Long productId, LocalDate metricsDate);

    void incrementTotalQuantity(Long productId, LocalDate metricsDate, int quantity);
}
