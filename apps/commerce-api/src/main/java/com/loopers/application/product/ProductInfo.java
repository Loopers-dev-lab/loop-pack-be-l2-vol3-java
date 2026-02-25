package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

import java.time.ZonedDateTime;

public record ProductInfo(
    Long id,
    Long brandId,
    String brandName,
    String name,
    Long price,
    String description,
    int stockQuantity,
    String status,
    long likeCount,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static ProductInfo from(ProductModel product, long likeCount) {
        return new ProductInfo(
            product.getId(),
            product.getBrand().getId(),
            product.getBrand().getName(),
            product.getName(),
            product.getPrice(),
            product.getDescription(),
            product.getStockQuantity(),
            product.getStatus().name(),
            likeCount,
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}
