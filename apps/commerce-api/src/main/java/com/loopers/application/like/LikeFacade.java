package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class LikeFacade {
    private final LikeService likeService;
    private final ProductService productService;

    public LikeInfo register(Long userId, Long productId) {
        productService.getVisibleProduct(productId);
        return likeService.register(userId, productId);
    }

    public void cancel(Long userId, Long productId) {
        likeService.cancel(userId, productId);
    }

    public List<LikeInfo> getLikesByUserId(Long userId) {
        List<LikeInfo> likes = likeService.getLikesByUserId(userId);
        if (likes.isEmpty()) return likes;

        List<Long> productIds = likes.stream().map(LikeInfo::productId).toList();
        Set<Long> existingProductIds = productService.getVisibleProductsByIds(productIds)
                                                     .stream()
                                                     .map(ProductInfo::id)
                                                     .collect(Collectors.toSet());
        return likes.stream()
                    .filter(like -> existingProductIds.contains(like.productId()))
                    .toList();
    }
}
