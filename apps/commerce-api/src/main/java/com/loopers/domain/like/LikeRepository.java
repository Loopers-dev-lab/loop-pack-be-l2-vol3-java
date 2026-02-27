package com.loopers.domain.like;

import java.util.List;

public interface LikeRepository {
    Like save(Like like);
    List<Like> findAllByUserId(Long userId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
    void deleteAllByProductId(Long productId);
    void deleteAllByProductIds(List<Long> productIds);
}
