package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;

public record ProductInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    int price,
    int stock,
    int likeCount,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static ProductInfo from(Product product, Brand brand) {
        return new ProductInfo(
            product.getId(),
            product.getBrandId(),
            brand.getName(),
            product.getName(),
            product.getPrice().amount(),
            product.getStock().quantity(),
            product.getLikeCount(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}
