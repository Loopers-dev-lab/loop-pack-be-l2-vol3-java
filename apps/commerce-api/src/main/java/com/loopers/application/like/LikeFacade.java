package com.loopers.application.like;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class LikeFacade {
    private final LikeApplicationService likeService;
    private final ProductApplicationService productService;

    @Transactional
    public LikeInfo register(Long userId, Long productId) {
        LikeInfo like = likeService.register(userId, productId);
        productService.increaseLikeCount(productId);
        return like;
    }

    @Transactional
    public void cancel(Long userId, Long productId) {
        if (likeService.cancel(userId, productId)) {
            productService.decreaseLikeCount(productId);
        }
    }

    public List<LikedProductInfo> getLikedProductsByUserId(Long userId) {
        List<LikeInfo> likes = likeService.getLikesByUserId(userId);
        if (likes.isEmpty()) return List.of();

        List<Long> productIds = likes.stream().map(LikeInfo::productId).toList();
        Map<Long, ProductInfo> productMap = productService.getActiveProductsByIds(productIds)
                                                          .stream()
                                                          .collect(Collectors.toMap(ProductInfo::id, p -> p));

        return likes.stream()
                    .filter(like -> productMap.containsKey(like.productId()))
                    .map(like -> new LikedProductInfo(like.id(), productMap.get(like.productId()), like.createdAt()))
                    .toList();
    }
}
