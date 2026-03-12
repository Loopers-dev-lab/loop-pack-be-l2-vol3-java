package com.loopers.application.like;

import com.loopers.domain.like.LikeResult;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.like.LikeToggleService;
import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class LikeTransactionService {

    private final LikeService likeService;
    private final ProductService productService;
    private final LikeToggleService likeToggleService;

    @Transactional
    public void doLike(Long userId, Long productId) {
        Optional<LikeModel> existing = likeService.findByUserIdAndProductId(userId, productId);

        LikeResult result = likeToggleService.like(existing, userId, productId);
        result.newLike().ifPresent(likeService::save);

        if (result.countChanged()) {
            productService.incrementLikeCount(productId);
        }
    }

    @Transactional
    public void doUnlike(Long userId, Long productId) {
        Optional<LikeModel> activeLike = likeService.findActiveLike(userId, productId);
        if (activeLike.isEmpty()) return;

        likeToggleService.unlike(activeLike.get());
        productService.decrementLikeCount(activeLike.get().productId());
    }
}
