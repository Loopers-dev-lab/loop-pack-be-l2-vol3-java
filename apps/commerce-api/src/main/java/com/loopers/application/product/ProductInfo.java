package com.loopers.application.product;

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
    public static ProductInfo from(Product product, String brandName) {
        return new ProductInfo(
                product.getId(),
                product.getBrandId(),
                brandName,
                product.getName(),
                product.getPrice().getAmount(),
                product.getStock().getQuantity(),
                product.getLikeCount(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
