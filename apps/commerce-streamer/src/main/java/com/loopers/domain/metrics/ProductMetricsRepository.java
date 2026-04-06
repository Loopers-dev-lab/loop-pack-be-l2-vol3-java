package com.loopers.domain.metrics;

public interface ProductMetricsRepository {

    void upsertLike(Long productId, int delta);

    void upsertOrder(Long productId, long quantity);

    void upsertView(Long productId);
}
