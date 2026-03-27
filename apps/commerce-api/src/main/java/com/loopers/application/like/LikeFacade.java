package com.loopers.application.like;

import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final LikeTransactionService likeTransactionService;

    public void like(Long userId, Long productId) {
        likeTransactionService.doLike(userId, productId);
    }

    public void unlike(Long userId, Long productId) {
        likeTransactionService.doUnlike(userId, productId);
    }

    @Transactional(readOnly = true)
    public Page<LikeModel> getMyLikes(Long userId, Pageable pageable) {
        return likeService.getMyLikes(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<LikeWithProduct> getMyLikesWithProducts(Long userId, Pageable pageable) {
        Page<LikeModel> likes = likeService.getMyLikes(userId, pageable);
        return likes.map(like -> {
            ProductModel product = productService.getById(like.productId());
            return new LikeWithProduct(
                like.getId(),
                product.getId(),
                product.getName(),
                product.getPrice().value(),
                like.getCreatedAt()
            );
        });
    }
}
