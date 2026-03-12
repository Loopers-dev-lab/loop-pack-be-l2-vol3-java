package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface LikeRepository {

    LikeModel save(LikeModel like);

    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);

    Optional<LikeModel> findByUserIdAndProductIdAndDeletedAtIsNull(Long userId, Long productId);

    Page<LikeModel> findActiveLikesWithActiveProduct(Long userId, Pageable pageable);
}
