package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStatsModel;
import com.loopers.domain.product.ProductStatsRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ProductStatsRepositoryImpl implements ProductStatsRepository {

    private final ProductStatsJpaRepository productStatsJpaRepository;

    public ProductStatsRepositoryImpl(ProductStatsJpaRepository productStatsJpaRepository) {
        this.productStatsJpaRepository = productStatsJpaRepository;
    }

    @Override
    public void createIfAbsent(Long productId) {
        productStatsJpaRepository.insertIgnore(productId);
    }

    @Override
    public void incrementLikeCount(Long productId) {
        productStatsJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public void decrementLikeCount(Long productId) {
        productStatsJpaRepository.decrementLikeCount(productId);
    }

    @Override
    public Optional<ProductStatsModel> findByProductId(Long productId) {
        return productStatsJpaRepository.findById(productId);
    }

    @Override
    public Map<Long, Long> findLikeCountByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productStatsJpaRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductStatsModel::getProductId, ProductStatsModel::getLikeCount));
    }
}
