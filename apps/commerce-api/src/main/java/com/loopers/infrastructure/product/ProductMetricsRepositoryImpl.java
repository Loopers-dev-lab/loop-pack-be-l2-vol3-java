package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductMetricsModel;
import com.loopers.domain.product.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    public void incrementLikeCount(Long refProductId) {
        productMetricsJpaRepository.incrementLikeCount(refProductId);
    }

    @Override
    public void decrementLikeCount(Long refProductId) {
        productMetricsJpaRepository.decrementLikeCount(refProductId);
    }
}
