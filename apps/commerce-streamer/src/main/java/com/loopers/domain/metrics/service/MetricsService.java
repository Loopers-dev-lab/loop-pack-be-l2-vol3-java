package com.loopers.domain.metrics.service;

import com.loopers.domain.ranking.model.ProductMetrics;
import com.loopers.infrastructure.metrics.entity.ProductMetricsEntity;
import com.loopers.infrastructure.metrics.repository.ProductMetricsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class MetricsService {

    private final ProductMetricsJpaRepository metricsRepository;

    public void incrementViewCount(Long productId, long version) {
        ProductMetricsEntity metrics = findOrCreate(productId);
        if (version <= metrics.getVersion()) return;
        metrics.incrementViewCount();
        metrics.updateVersion(version);
        metricsRepository.save(metrics);
    }

    public void incrementLikeCount(Long productId, long version) {
        ProductMetricsEntity metrics = findOrCreate(productId);
        if (version <= metrics.getVersion()) return;
        metrics.incrementLikeCount();
        metrics.updateVersion(version);
        metricsRepository.save(metrics);
    }

    public void decrementLikeCount(Long productId, long version) {
        ProductMetricsEntity metrics = findOrCreate(productId);
        if (version <= metrics.getVersion()) return;
        metrics.decrementLikeCount();
        metrics.updateVersion(version);
        metricsRepository.save(metrics);
    }

    public void incrementOrderCount(Long productId) {
        ProductMetricsEntity metrics = findOrCreate(productId);
        metrics.incrementOrderCount();
        metricsRepository.save(metrics);
    }

    public List<ProductMetrics> findChangedAfter(LocalDateTime since) {
        return metricsRepository.findByUpdatedAtAfter(since).stream()
                .map(e -> new ProductMetrics(e.getProductId(), e.getViewCount(), e.getLikeCount(), e.getOrderCount()))
                .toList();
    }

    private ProductMetricsEntity findOrCreate(Long productId) {
        return metricsRepository.findById(productId)
                .orElseGet(() -> ProductMetricsEntity.createNew(productId));
    }
}
