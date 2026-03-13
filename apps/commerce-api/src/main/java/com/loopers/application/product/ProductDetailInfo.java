package com.loopers.application.product;

import com.loopers.domain.product.Brand;
import com.loopers.domain.product.Product;

public record ProductDetailInfo(
    Long id,
    String name,
    Long price,
    int stockQuantity,
    BrandInfo brand,
    long likeCount
) {
    public record BrandInfo(Long id, String name) {}

    public static ProductDetailInfo of(Product product, Brand brand, long likeCount) {
        BrandInfo brandInfo = brand != null ? new BrandInfo(brand.getId(), brand.getName()) : null;
        return new ProductDetailInfo(
            product.getId(),
            product.getName(),
            product.getPrice(),
            product.getStockQuantity(),
            brandInfo,
            likeCount
        );
    }
}
