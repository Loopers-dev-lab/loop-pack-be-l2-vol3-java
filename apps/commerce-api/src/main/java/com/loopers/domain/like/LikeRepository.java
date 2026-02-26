package com.loopers.domain.like;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * 좋아요 영속성 인터페이스.
 * 구현체는 infrastructure 레이어에 둔다.
 */
public interface LikeRepository {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    Optional<LikeModel> findByUserIdAndProductId(Long userId, Long productId);

    LikeModel save(LikeModel like);

    void delete(LikeModel like);

    Page<LikeModel> findByUserId(Long userId, Pageable pageable);

    long countByProductId(Long productId);

    /**
     * 상품 ID 목록별 좋아요 수를 반환한다. 목록에 없는 상품은 0으로 간주한다.
     */
    Map<Long, Long> countByProductIds(java.util.Collection<Long> productIds);
}
