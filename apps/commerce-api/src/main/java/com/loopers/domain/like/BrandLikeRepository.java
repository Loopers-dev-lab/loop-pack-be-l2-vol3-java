package com.loopers.domain.like;

import java.util.List;
import java.util.Optional;

public interface BrandLikeRepository {
    BrandLike save(BrandLike brandLike);
    Optional<BrandLike> findByUserIdAndBrandId(Long userId, Long brandId);
    boolean existsByUserIdAndBrandId(Long userId, Long brandId);
    void delete(BrandLike brandLike);

    /** 삭제/비활성 브랜드 제외, 최근 좋아요순 */
    List<BrandLike> findActiveByUserId(Long userId, int page, int size);
    long countActiveByUserId(Long userId);
}
