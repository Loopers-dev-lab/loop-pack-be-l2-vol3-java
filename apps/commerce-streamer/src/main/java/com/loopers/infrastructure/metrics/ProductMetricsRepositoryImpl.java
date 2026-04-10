package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.MetricsLikeCountMismatch;
import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 상품 지표 리포지토리 구현체.
 */
@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public Optional<ProductMetricsModel> findById(Long productId) {
        return jpaRepository.findById(productId);
    }

    @Override
    public ProductMetricsModel save(ProductMetricsModel model) {
        return jpaRepository.save(model);
    }

    @Override
    public void incrementViewCount(Long productId) {
        jpaRepository.incrementViewCount(productId);
    }

    @Override
    public void incrementLikeCount(Long productId) {
        jpaRepository.incrementLikeCount(productId);
    }

    @Override
    public void decrementLikeCount(Long productId) {
        jpaRepository.decrementLikeCount(productId);
    }

    @Override
    public void incrementOrderCount(Long productId, long amount) {
        jpaRepository.incrementOrderCount(productId, amount);
    }

    @Override
    public List<ProductMetricsModel> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<MetricsLikeCountMismatch> findLikeCountMismatches() {
        return jpaRepository.findLikeCountMismatchesRaw().stream()
            .map(row -> new MetricsLikeCountMismatch(
                ((Number) row[0]).longValue(),
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue()))
            .toList();
    }

    @Override
    public void forceUpdateLikeCount(Long productId, long likeCount) {
        jpaRepository.forceUpdateLikeCount(productId, likeCount);
    }
}
