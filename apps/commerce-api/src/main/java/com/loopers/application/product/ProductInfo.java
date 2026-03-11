package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

public record ProductInfo(
    Long id,
    String name,
    Integer price,
    Integer stock,
    Integer likeCount,
    Long brandId,
    String brandName,
    String brandDescription
) {

    public static ProductInfo of(Product product, Brand brand) {
        return new ProductInfo(
            product.getId(),
            product.getName(),
            product.getPrice().value(),
            product.getStock(),
            product.getLikeCount(),
            brand.getId(),
            brand.getName(),
            brand.getDescription()
        );
    }
}
