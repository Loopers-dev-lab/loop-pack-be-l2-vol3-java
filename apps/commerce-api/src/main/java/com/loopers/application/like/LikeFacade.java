package com.loopers.application.like;

import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;

    // Command

    @Transactional
    public void like(Long userId, Long productId) {
        productService.getActiveProduct(productId);

        boolean created = likeService.like(userId, productId);
        if (created) {
            productService.incrementLikeCount(productId);
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        productService.getActiveProduct(productId);

        boolean deleted = likeService.unlike(userId, productId);
        if (deleted) {
            productService.decrementLikeCount(productId);
        }
    }
}
