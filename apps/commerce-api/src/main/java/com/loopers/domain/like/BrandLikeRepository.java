package com.loopers.domain.like;

import java.util.Optional;

public interface BrandLikeRepository {
    BrandLike save(BrandLike brandLike);
    Optional<BrandLike> findByUserIdAndBrandId(Long userId, Long brandId);
    boolean existsByUserIdAndBrandId(Long userId, Long brandId);
    void delete(BrandLike brandLike);
}
