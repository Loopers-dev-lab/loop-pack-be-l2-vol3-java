package com.loopers.domain.product;

import java.util.List;

public interface ProductLikeStatsRepository {
    ProductLikeStats save(ProductLikeStats stats);
    List<ProductLikeStats> saveAll(List<ProductLikeStats> statsList);
    List<ProductLikeStats> findAll();
    void syncAllFromLikes();
    int correctProductLikeCounts();
}
