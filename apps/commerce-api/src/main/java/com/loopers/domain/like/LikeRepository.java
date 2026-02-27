package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface LikeRepository {
    Like save(Like like);
    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);
    List<Like> findAllByUserId(Long userId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
    void deleteAllByProductId(Long productId);
    void deleteAllByProductIds(List<Long> productIds);
}
