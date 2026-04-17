package com.loopers.infrastructure.metrics.repository;

import com.loopers.domain.metrics.repository.ProductMetricsRepository;
import com.loopers.infrastructure.metrics.entity.ProductMetricsEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public Optional<ProductMetricsEntity> findById(Long productId) {
        return productMetricsJpaRepository.findById(productId);
    }

    @Override
    public ProductMetricsEntity save(ProductMetricsEntity metrics) {
        return productMetricsJpaRepository.save(metrics);
    }

    @Override
    public List<ProductMetricsEntity> findByUpdatedAtAfter(LocalDateTime since) {
        return productMetricsJpaRepository.findByUpdatedAtAfter(since);
    }
}
