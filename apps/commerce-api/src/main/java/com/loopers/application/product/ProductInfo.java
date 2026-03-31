package com.loopers.application.product;

import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;

public record ProductInfo(
        Long id,
        BrandSummary brand,
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
    public record BrandSummary(Long id, String name) {}

    public static ProductInfo from(Product product) {
        return new ProductInfo(
                product.getId(),
                new BrandSummary(product.getBrandId(), null),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                0,
                product.getVisibility(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getDeletedAt()
        );
    }

    public ProductInfo withBrand(BrandSummary brand) {
        return new ProductInfo(
                id, brand, name, description,
                price, stockQuantity, likeCount, visibility,
                createdAt, updatedAt, deletedAt
        );
    }
}
