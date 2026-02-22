package com.loopers.domain.like;

import com.loopers.domain.brand.BrandService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.LikeErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class BrandLikeService {

    private final BrandLikeRepository brandLikeRepository;
    private final BrandService brandService;

    public BrandLikeService(BrandLikeRepository brandLikeRepository, BrandService brandService) {
        this.brandLikeRepository = brandLikeRepository;
        this.brandService = brandService;
    }

    @Transactional
    public BrandLike like(Long userId, Long brandId) {
        brandService.getActiveBrand(brandId);
        if (brandLikeRepository.existsByUserIdAndBrandId(userId, brandId)) {
            throw new CoreException(LikeErrorType.ALREADY_LIKED);
        }
        BrandLike brandLike = BrandLike.create(userId, brandId);
        return brandLikeRepository.save(brandLike);
    }

    @Transactional
    public void unlike(Long userId, Long brandId) {
        BrandLike brandLike = brandLikeRepository.findByUserIdAndBrandId(userId, brandId)
                .orElseThrow(() -> new CoreException(LikeErrorType.LIKE_NOT_FOUND));
        brandLikeRepository.delete(brandLike);
    }

    @Transactional(readOnly = true)
    public List<BrandLike> getMyBrandLikes(Long userId, int page, int size) {
        return brandLikeRepository.findActiveByUserId(userId, page, size);
    }

    @Transactional(readOnly = true)
    public long countMyBrandLikes(Long userId) {
        return brandLikeRepository.countActiveByUserId(userId);
    }
}
