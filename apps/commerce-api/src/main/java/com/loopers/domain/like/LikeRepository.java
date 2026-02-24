package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 좋아요 영속성 인터페이스.
 * 구현체는 infrastructure 레이어에 둔다.
 */
public interface LikeRepository {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    LikeModel save(LikeModel like);

    void delete(LikeModel like);

    Page<LikeModel> findByUserId(Long userId, Pageable pageable);

    long countByProductId(Long productId);
}
