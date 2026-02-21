package com.loopers.domain.like;

public interface LikeRepository {

    Like save(Like like);

    boolean existsByUserIdAndProductId(Long userId, Long productId);
}
