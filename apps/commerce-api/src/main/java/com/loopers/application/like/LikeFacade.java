package com.loopers.application.like;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.Like;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final BrandService brandService;

    @Transactional
    public void like(Long userId, Long productId) {
        productService.getById(productId);
        likeService.like(userId, productId);
        productService.incrementLikeCount(productId);
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        productService.getById(productId);
        likeService.unlike(userId, productId);
        productService.decrementLikeCount(productId);
    }

    public List<LikeInfo> getMyLikes(Long userId) {
        List<Like> likes = likeService.getMyLikes(userId);

        Set<Long> productIds = likes.stream()
            .map(Like::getProductId)
            .collect(Collectors.toSet());

        Map<Long, Product> productMap = productService.getByIds(productIds);

        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());
        Map<Long, Brand> brandMap = brandService.getByIds(brandIds);

        return likes.stream()
            .filter(like -> productMap.containsKey(like.getProductId()))
            .map(like -> {
                Product product = productMap.get(like.getProductId());
                Brand brand = brandMap.get(product.getBrandId());
                return LikeInfo.from(like, product, brand);
            })
            .toList();
    }
}
