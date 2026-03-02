package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record ProductInfo(
        Long id,
        Long brandId,
        String brandName,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        String imageUrl,
        Integer likesCount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static ProductInfo from(Product product, Brand brand) {
        return new ProductInfo(
                product.getId(),
                product.getBrandId(),
                brand.getName(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getImageUrl(),
                product.getLikesCount(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
