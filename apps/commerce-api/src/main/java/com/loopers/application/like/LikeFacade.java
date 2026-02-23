package com.loopers.application.like;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LikeFacade {
    private final LikeAppService likeAppService;
    private final ProductAppService productAppService;

    public boolean toggleLike(Long userId, Long productId) {
        productAppService.getById(productId);
        return likeAppService.toggleLike(userId, productId);
    }

    public List<LikeInfo> getLikedProducts(Long userId) {
        List<Like> likes = likeAppService.getLikesByUserId(userId);

        return likes.stream()
                .map(like -> {
                    Product product = productAppService.getById(like.getProductId());
                    return LikeInfo.of(like, product);
                })
                .toList();
    }
}
