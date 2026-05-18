package com.loopers.domain.metrics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public interface ProductMetricsRepository {

    void upsertViewCount(Long productId, LocalDate metricDate, int delta);

    void upsertLikeCount(Long productId, LocalDate metricDate, int delta);

    void upsertSalesCount(Long productId, LocalDate metricDate, int delta);

    void upsertPrice(Long productId, LocalDate metricDate, BigDecimal price, Instant occurredAt);
}
