package com.loopers.application.product;

import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;

public record ProductInfo(
        Long id,
        Long brandId,
        String name,
        String description,
        Integer price,
        Integer stockQuantity,
        Integer likeCount,
        Product.Visibility visibility,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
) {
    public static ProductInfo from(Product product) {
        return new ProductInfo(
                product.getId(),
                product.getBrandId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getLikeCount(),
                product.getVisibility(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getDeletedAt()
        );
    }
}
