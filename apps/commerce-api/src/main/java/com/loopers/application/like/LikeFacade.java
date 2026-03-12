package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;

    @Transactional
    public void like(Long userId, Long productId) {
        ProductModel product = productService.getProduct(productId);

        Optional<LikeModel> existing = likeService.findByUserIdAndProductId(userId, productId);

        if (existing.isEmpty()) {
            likeService.save(new LikeModel(userId, productId));
            product.incrementLikeCount();
        } else if (existing.get().getDeletedAt() != null) {
            existing.get().restore();
            product.incrementLikeCount();
        }
        // else: 이미 활성 좋아요 존재 → 멱등, 아무것도 안 함
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        Optional<LikeModel> existing = likeService.findActiveLike(userId, productId);

        if (existing.isPresent()) {
            existing.get().delete();
            ProductModel product = productService.getProduct(existing.get().productId());
            product.decrementLikeCount();
        }
        // else: 좋아요가 없음 → 멱등, 아무것도 안 함
    }

    @Transactional(readOnly = true)
    public Page<LikeModel> getMyLikes(Long userId, Pageable pageable) {
        return likeService.getMyLikes(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LikeWithProduct> getMyLikesWithProducts(Long userId, Pageable pageable) {
        Page<LikeModel> likes = likeService.getMyLikes(userId, pageable);
        return likes.map(like -> {
            ProductModel product = productService.getProduct(like.productId());
            return new LikeWithProduct(like, product);
        });
    }
}
