package com.loopers.application.like;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

        if (likes.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = likes.stream().map(Like::getProductId).toList();
        Map<Long, Product> productMap = productAppService.getByIds(productIds);

        return likes.stream()
                .map(like -> LikeInfo.of(like, productMap.get(like.getProductId())))
                .toList();
    }
}
