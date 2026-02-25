package com.loopers.application.like;

import com.loopers.application.brand.BrandService;
import com.loopers.application.product.ProductService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
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

    public Page<LikeProductInfo> getLikedProducts(Long userId, Pageable pageable) {
        Page<Like> likes = likeService.findLikedProducts(userId, pageable);

        List<Long> productIds = likes.getContent().stream()
                .map(Like::getProductId)
                .toList();

        Map<Long, Product> productMap = productService.getProducts(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId)
                .distinct()
                .toList();

        Map<Long, Brand> brandMap = brandService.getBrands(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Function.identity()));

        return likes.map(like -> {
            Product product = productMap.get(like.getProductId());
            Brand brand = brandMap.get(product.getBrandId());
            return LikeProductInfo.from(product, brand.getName());
        });
    }
}
