package com.loopers.application.product;

import com.loopers.domain.product.Product;

public record ProductInfo(
    Long productId,
    Long brandId,
    String name,
    Long price,
    String description,
    Integer stockQuantity,
    long likeCount
) {

    public static ProductInfo from(Product product) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getPrice(),
            product.getDescription(),
            product.getStockQuantity().value(),
            product.getLikeCount()
        );
    }
}
