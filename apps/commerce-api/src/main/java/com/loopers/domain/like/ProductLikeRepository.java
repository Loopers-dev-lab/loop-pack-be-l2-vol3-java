package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface ProductLikeRepository {
    ProductLike save(ProductLike productLike);
    Optional<ProductLike> findByUserIdAndProductId(Long userId, Long productId);
    boolean existsByUserIdAndProductId(Long userId, Long productId);
    void delete(ProductLike productLike);

    /** 삭제된 상품 제외, 최근 좋아요순 */
    List<ProductLike> findActiveByUserId(Long userId, int page, int size);
    long countActiveByUserId(Long userId);
}
