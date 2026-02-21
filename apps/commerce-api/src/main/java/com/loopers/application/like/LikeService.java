package com.loopers.application.like;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void likeProduct(Long userId, Long productId) {
        validateProductExists(productId);
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }
        Like like = Like.create(userId, productId);
        likeRepository.save(like);
    }

    @Transactional
    public void unlikeProduct(Long userId, Long productId) {
        validateProductExists(productId);
        likeRepository.findByUserIdAndProductId(userId, productId)
                .ifPresent(likeRepository::delete);
    }

    private void validateProductExists(Long productId) {
        if (!productRepository.existsByIdAndDeletedAtIsNull(productId)) {
            throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
        }
    }
}
