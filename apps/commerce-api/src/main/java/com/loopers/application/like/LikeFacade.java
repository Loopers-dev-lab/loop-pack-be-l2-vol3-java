package com.loopers.application.like;

import com.loopers.application.brand.BrandService;
import com.loopers.application.product.ProductService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LikeFacade {

    private final LikeService likeService;
    private final ProductService productService;
    private final BrandService brandService;

    // Command

    @Transactional
    public void like(Long userId, Long productId) {
        productService.validateActiveProduct(productId);

        boolean created = likeService.like(userId, productId);
        if (created) {
            productService.incrementLikeCount(productId);
        }
    }

    @Transactional
    public void unlike(Long userId, Long productId) {
        boolean deleted = likeService.unlike(userId, productId);
        if (deleted) {
            productService.decrementLikeCountIfPositive(productId);
        }
    }

    // Query

    @Transactional(readOnly = true)
    public Page<LikeProductInfo> getLikedProducts(Long userId, Pageable pageable) {
        Page<Like> likes = likeService.findLikedActiveProducts(userId, pageable);

        Set<Long> productIds = likes.getContent().stream()
                .map(Like::getProductId)
                .collect(Collectors.toSet());

        Map<Long, Product> productMap = productService.getProductsMapByIds(productIds);

        Set<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId)
                .collect(Collectors.toSet());

        Map<Long, Brand> brandMap = brandService.getBrandsMapByIds(brandIds);

        for (Like like : likes.getContent()) {
            if (!productMap.containsKey(like.getProductId())) {
                throw new CoreException(ErrorType.NOT_FOUND,
                        "상품 매핑 누락. likeId=" + like.getId() + ", productId=" + like.getProductId());
            }
        }

        for (Product product : productMap.values()) {
            if (!brandMap.containsKey(product.getBrandId())) {
                throw new CoreException(ErrorType.NOT_FOUND,
                        "브랜드 매핑 누락. productId=" + product.getId() + ", brandId=" + product.getBrandId());
            }
        }

        return likes.map(like -> {
            Product product = productMap.get(like.getProductId());
            return LikeProductInfo.from(product, brandMap.get(product.getBrandId()).getName());
        });
    }
}
