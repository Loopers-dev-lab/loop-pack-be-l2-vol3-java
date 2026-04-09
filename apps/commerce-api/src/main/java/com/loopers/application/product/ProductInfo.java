package com.loopers.application.product;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.loopers.domain.product.ProductModel;

import java.time.ZonedDateTime;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
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
    ZonedDateTime updatedAt,
    Long ranking
) {
    public static ProductInfo from(ProductModel product) {
        return new ProductInfo(
            product.getId(),
            product.getBrand().getId(),
            product.getBrand().getName(),
            product.getName(),
            product.getPrice(),
            product.getDescription(),
            product.getStockQuantity(),
            product.getStatus().name(),
            product.getLikeCount(),
            product.getCreatedAt(),
            product.getUpdatedAt(),
            null
        );
    }

    public ProductInfo withRanking(Long ranking) {
        return new ProductInfo(
            id,
            brandId,
            brandName,
            name,
            price,
            description,
            stockQuantity,
            status,
            likeCount,
            createdAt,
            updatedAt,
            ranking
        );
    }
}
