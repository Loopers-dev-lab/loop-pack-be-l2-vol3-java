package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public Optional<ProductMetricsModel> findByRefProductId(Long refProductId) {
        return productMetricsJpaRepository.findByRefProductId(refProductId);
    }

    @Override
    public ProductMetricsModel save(ProductMetricsModel model) {
        return productMetricsJpaRepository.save(model);
    }

    @Override
    public void upsertLikeDelta(Long refProductId, int delta, LocalDateTime eventAt) {
        productMetricsJpaRepository.upsertLikeDelta(refProductId, delta, eventAt);
    }
}
