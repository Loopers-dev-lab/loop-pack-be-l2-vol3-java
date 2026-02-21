package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.LikeErrorType;
public class BrandLikeService {

    private final BrandLikeRepository brandLikeRepository;

    public BrandLikeService(BrandLikeRepository brandLikeRepository) {
        this.brandLikeRepository = brandLikeRepository;
    }

    public BrandLike like(Long userId, Long brandId) {
        if (brandLikeRepository.existsByUserIdAndBrandId(userId, brandId)) {
            throw new CoreException(LikeErrorType.ALREADY_LIKED);
        }
        BrandLike brandLike = BrandLike.create(userId, brandId);
        return brandLikeRepository.save(brandLike);
    }

    public void unlike(Long userId, Long brandId) {
        BrandLike brandLike = brandLikeRepository.findByUserIdAndBrandId(userId, brandId)
                .orElseThrow(() -> new CoreException(LikeErrorType.LIKE_NOT_FOUND));
        brandLikeRepository.delete(brandLike);
    }
}
