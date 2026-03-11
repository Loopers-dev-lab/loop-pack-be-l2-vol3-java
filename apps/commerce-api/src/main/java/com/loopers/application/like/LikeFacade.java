package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;

    public void toggleLike(Long productId, Long userId) {
        if (likeService.isLiked(productId, userId)) {
            likeService.unlike(productId, userId);
            productService.decreaseLikeCount(productId);
        } else {
            likeService.like(productId, userId);
            productService.increaseLikeCount(productId);
        }
    }
}
