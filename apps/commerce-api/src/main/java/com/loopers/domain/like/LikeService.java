package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.LikeErrorType;
public class LikeService {

    private final ProductLikeRepository productLikeRepository;

    public LikeService(ProductLikeRepository productLikeRepository) {
        this.productLikeRepository = productLikeRepository;
    }

    public ProductLike like(Long userId, Long productId) {
        if (productLikeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(LikeErrorType.ALREADY_LIKED);
        }
        ProductLike productLike = ProductLike.create(userId, productId);
        return productLikeRepository.save(productLike);
    }

    public void unlike(Long userId, Long productId) {
        ProductLike productLike = productLikeRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new CoreException(LikeErrorType.LIKE_NOT_FOUND));
        productLikeRepository.delete(productLike);
    }
}
