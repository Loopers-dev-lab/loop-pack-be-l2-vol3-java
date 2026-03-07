package com.loopers.domain.like;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.LikeErrorType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class LikeService {

    private final ProductLikeRepository productLikeRepository;

    public LikeService(ProductLikeRepository productLikeRepository) {
        this.productLikeRepository = productLikeRepository;
    }

    @Transactional
    public void like(Long userId, Long productId) {
        if (productLikeRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new CoreException(LikeErrorType.ALREADY_LIKED);
        }
        productLikeRepository.save(ProductLike.of(userId, productId));
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        ProductLike productLike = productLikeRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new CoreException(LikeErrorType.LIKE_NOT_FOUND));
        productLikeRepository.delete(productLike);
    }

    @Transactional(readOnly = true)
    public List<ProductLike> getMyProductLikes(Long userId, int page, int size) {
        return productLikeRepository.findActiveByUserId(userId, page, size);
    }

    @Transactional(readOnly = true)
    public long countMyProductLikes(Long userId) {
        return productLikeRepository.countActiveByUserId(userId);
    }
}
