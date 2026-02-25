package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class LikeFacade {
    private final LikeService likeService;
    private final ProductService productService;

    public LikeInfo register(Long userId, Long productId) {
        productService.ensureVisibleProduct(productId);
        LikeInfo like = likeService.register(userId, productId);
        productService.increaseLikeCount(productId);
        return like;
    }

    public void cancel(Long userId, Long productId) {
        likeService.cancel(userId, productId);
        productService.decreaseLikeCount(productId);
    }

    public List<LikedProductInfo> getLikedProductsByUserId(Long userId) {
        List<LikeInfo> likes = likeService.getLikesByUserId(userId);
        if (likes.isEmpty()) return List.of();

        List<Long> productIds = likes.stream().map(LikeInfo::productId).toList();
        Map<Long, ProductInfo> productMap = productService.getVisibleProductsByIds(productIds)
                                                          .stream()
                                                          .collect(Collectors.toMap(ProductInfo::id, p -> p));

        return likes.stream()
                    .filter(like -> productMap.containsKey(like.productId()))
                    .map(like -> new LikedProductInfo(like.id(), productMap.get(like.productId()), like.createdAt()))
                    .toList();
    }
}
