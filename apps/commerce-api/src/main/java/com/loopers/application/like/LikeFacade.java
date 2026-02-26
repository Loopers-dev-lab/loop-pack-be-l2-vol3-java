package com.loopers.application.like;

import com.loopers.application.brand.BrandService;
import com.loopers.application.product.ProductService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final BrandService brandService;

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

    // Query

    public Page<LikeProductInfo> getLikedProducts(Long userId, @Valid LikeRequest.ListLiked request) {
        Page<Like> likes = likeService.findLikedActiveProducts(userId, request.toPageable());

        Set<Long> productIds = likes.getContent().stream()
                .map(Like::getProductId)
                .collect(Collectors.toSet());

        Map<Long, Product> productMap = productService.getProductsMapByIds(productIds);

        Set<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());

        Map<Long, Brand> brandMap = brandService.getBrandsMapByIds(brandIds);

        return likes.map(like -> {
            Product product = productMap.get(like.getProductId());
            Brand brand = brandMap.get(product.getBrandId());
            return LikeProductInfo.from(product, brand.getName());
        });
    }
}
