package com.loopers.domain.metrics.service;

import com.loopers.domain.metrics.repository.ProductMetricsDailyRepository;
import com.loopers.domain.metrics.repository.ProductMetricsRepository;
import com.loopers.domain.ranking.model.ProductMetrics;
import com.loopers.infrastructure.metrics.entity.ProductMetricsEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Component
public class MetricsService {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;

    private final ProductMetricsRepository metricsRepository;
    private final ProductMetricsDailyRepository dailyMetricsRepository;

    @Transactional
    public void incrementViewCount(Long productId) {
        ProductMetricsEntity metrics = findOrCreate(productId);
        metrics.incrementViewCount();
        metricsRepository.save(metrics);
        dailyMetricsRepository.incrementViewCount(LocalDate.now(), productId, VIEW_WEIGHT);
    }

    @Transactional
    public void incrementLikeCount(Long productId) {
        ProductMetricsEntity metrics = findOrCreate(productId);
        metrics.incrementLikeCount();
        metricsRepository.save(metrics);
        dailyMetricsRepository.incrementLikeCount(LocalDate.now(), productId, LIKE_WEIGHT);
    }

    @Transactional
    public void decrementLikeCount(Long productId) {
        ProductMetricsEntity metrics = findOrCreate(productId);
        metrics.decrementLikeCount();
        metricsRepository.save(metrics);
        dailyMetricsRepository.decrementLikeCount(LocalDate.now(), productId, -LIKE_WEIGHT);
    }

    @Transactional
    public void incrementOrderCount(Long productId) {
        ProductMetricsEntity metrics = findOrCreate(productId);
        metrics.incrementOrderCount();
        metricsRepository.save(metrics);
        dailyMetricsRepository.incrementOrderCount(LocalDate.now(), productId, ORDER_WEIGHT);
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
