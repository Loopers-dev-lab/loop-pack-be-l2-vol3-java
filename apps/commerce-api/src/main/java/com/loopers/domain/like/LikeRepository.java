package com.loopers.domain.like;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface LikeRepository {
    Like save(Like like);
    void delete(Like like);
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
    long countByProductId(Long productId);
    List<Like> findByUserId(Long userId);
    Map<Long, Long> countByProductIdIn(List<Long> productIds);
    Set<Long> findLikedProductIds(Long userId, List<Long> productIds);
}
