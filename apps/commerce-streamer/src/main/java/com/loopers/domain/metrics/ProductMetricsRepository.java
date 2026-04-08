package com.loopers.domain.metrics;

import java.time.LocalDateTime;

public interface ProductMetricsRepository {

    void upsertLike(Long productId, int delta, LocalDateTime metricHour);

    void upsertOrder(Long productId, long quantity, LocalDateTime metricHour);

    void upsertView(Long productId, LocalDateTime metricHour);
}
