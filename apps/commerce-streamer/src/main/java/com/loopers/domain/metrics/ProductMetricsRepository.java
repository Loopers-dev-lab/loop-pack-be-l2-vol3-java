package com.loopers.domain.metrics;

import java.time.LocalDateTime;

public interface ProductMetricsRepository {

    void upsertLike(Long productId, int delta, LocalDateTime metricHour);

    void upsertOrder(Long productId, long quantity, long salesAmount, LocalDateTime metricHour);

    void upsertView(Long productId, LocalDateTime metricHour);
}
