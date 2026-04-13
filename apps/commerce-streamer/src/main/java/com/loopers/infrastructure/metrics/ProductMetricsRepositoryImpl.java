package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public Optional<ProductMetrics> findByProductIdAndMetricsDate(Long productId, LocalDate metricsDate) {
        return jpaRepository.findByProductIdAndMetricsDate(productId, metricsDate)
            .map(ProductMetricsEntity::toDomain);
    }

    @Override
    public ProductMetrics save(ProductMetrics metrics) {
        return ProductMetricsEntity.toDomain(jpaRepository.save(new ProductMetricsEntity(metrics)));
    }

    @Override
    public void upsertIfAbsent(Long productId, LocalDate metricsDate) {
        jpaRepository.upsertIfAbsent(productId, metricsDate);
    }

    @Override
    public void incrementLikeCount(Long productId, LocalDate metricsDate) {
        jpaRepository.incrementLikeCount(productId, metricsDate);
    }

    @Override
    public void decrementLikeCount(Long productId, LocalDate metricsDate) {
        jpaRepository.decrementLikeCount(productId, metricsDate);
    }

    @Override
    public void incrementViewCount(Long productId, LocalDate metricsDate) {
        jpaRepository.incrementViewCount(productId, metricsDate);
    }

    @Override
    public void incrementSalesCount(Long productId, LocalDate metricsDate) {
        jpaRepository.incrementSalesCount(productId, metricsDate);
    }

    @Override
    public void incrementTotalQuantity(Long productId, LocalDate metricsDate, int quantity) {
        jpaRepository.incrementTotalQuantity(productId, metricsDate, quantity);
    }
}
