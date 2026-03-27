package com.loopers.application.product;

import com.loopers.domain.product.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductInfo(
        Long id,
        Long brandId,
        String brandName,
        String name,
        BigDecimal price,
        Integer stockQuantity,
        String description,
        Integer likeCount,
        Status status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {

    public enum Status {
        ACTIVE, DELETED
    }

    public static ProductInfo from(Product product, String brandName, int stockQuantity) {
        return new ProductInfo(
                product.getId(),
                product.getBrandId(),
                brandName,
                product.getName(),
                product.getPrice(),
                stockQuantity,
                product.getDescription(),
                product.getLikeCount(),
                product.isDeleted() ? Status.DELETED : Status.ACTIVE,
                product.getCreatedAt().toLocalDateTime(),
                product.getUpdatedAt().toLocalDateTime(),
                product.getDeletedAt() != null ? product.getDeletedAt().toLocalDateTime() : null
        );
    }
}
