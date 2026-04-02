package com.loopers.fake;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 테스트용 Fake ProductMetricsRepository.
 */
public class FakeProductMetricsRepository implements ProductMetricsRepository {

    private final Map<Long, ProductMetrics> store = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(0);

    @Override
    public ProductMetrics save(ProductMetrics metrics) {
        Long id = metrics.getId() != null ? metrics.getId() : idGenerator.incrementAndGet();
        ProductMetrics saved = ProductMetrics.reconstitute(
            id,
            metrics.getProductId(),
            metrics.getLikeCount(),
            metrics.getViewCount(),
            metrics.getOrderCount(),
            metrics.getOrderTotalAmount(),
            metrics.getUpdatedAt(),
            metrics.getLastEventTimestamp()
        );
        store.put(metrics.getProductId(), saved);
        return saved;
    }

    @Override
    public Optional<ProductMetrics> findByProductId(Long productId) {
        return Optional.ofNullable(store.get(productId));
    }

    @Override
    public ProductMetrics getOrCreate(Long productId) {
        return findByProductId(productId)
            .orElseGet(() -> {
                ProductMetrics metrics = ProductMetrics.create(productId);
                return save(metrics);
            });
    }

    public void clear() {
        store.clear();
        idGenerator.set(0);
    }
}
