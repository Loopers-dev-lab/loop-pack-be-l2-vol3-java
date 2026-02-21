package com.loopers.domain.like;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface LikeRepository {

    Like save(Like like);

    Optional<Like> findByUserIdAndProductId(Long userId, Long productId);

    Slice<Like> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void delete(Like like);

    /**
     * 주어진 상품 ID 목록에 대해 상품별 좋아요 수를 조회한다.
     *
     * @param productIds 조회할 상품 ID 목록
     * @return key: productId, value: 해당 상품의 좋아요 수
     */
    Map<Long, Long> countByProductIdIn(List<Long> productIds);
}
