package com.loopers.domain.like;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LikeRepository {
    LikeModel save(LikeModel like);
    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);
    List<LikeModel> findByUserId(Long userId);
    void delete(LikeModel like);
    long countByProductId(Long productId);
    Map<Long, Long> countByProductIds(List<Long> productIds);
}
