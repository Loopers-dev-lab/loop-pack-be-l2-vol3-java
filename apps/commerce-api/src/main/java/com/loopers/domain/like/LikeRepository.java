package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {
    Like save(Like like);
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
    int deleteByUserIdAndProductId(Long userId, Long productId);
    void deleteAllByProductIdIn(List<Long> productIds);
    List<Like> findByUserId(Long userId);
}
