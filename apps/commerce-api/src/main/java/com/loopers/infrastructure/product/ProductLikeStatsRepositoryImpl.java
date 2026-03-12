package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductLikeStats;
import com.loopers.domain.product.ProductLikeStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductLikeStatsRepositoryImpl implements ProductLikeStatsRepository {

    private final ProductLikeStatsJpaRepository productLikeStatsJpaRepository;

    @Override
    public ProductLikeStats save(ProductLikeStats stats) {
        return productLikeStatsJpaRepository.save(stats);
    }

    @Override
    public List<ProductLikeStats> saveAll(List<ProductLikeStats> statsList) {
        return productLikeStatsJpaRepository.saveAll(statsList);
    }

    @Override
    public List<ProductLikeStats> findAll() {
        return productLikeStatsJpaRepository.findAll();
    }

    @Override
    public void syncAllFromLikes() {
        productLikeStatsJpaRepository.syncAllFromLikes();
    }

    @Override
    public int correctProductLikeCounts() {
        return productLikeStatsJpaRepository.correctProductLikeCounts();
    }
}
