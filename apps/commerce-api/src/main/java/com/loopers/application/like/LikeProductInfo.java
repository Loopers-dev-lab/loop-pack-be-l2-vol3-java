package com.loopers.application.like;

import com.loopers.domain.product.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LikeProductInfo(
        Long id,
        Long brandId,
        String brandName,
        String name,
        BigDecimal price,
        Integer stockQuantity,
        String description,
        Integer likeCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static LikeProductInfo from(Product product, String brandName) {
        return new LikeProductInfo(
                product.getId(),
                product.getBrandId(),
                brandName,
                product.getName(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getDescription(),
                product.getLikeCount(),
                product.getCreatedAt().toLocalDateTime(),
                product.getUpdatedAt().toLocalDateTime()
        );
    }
}
