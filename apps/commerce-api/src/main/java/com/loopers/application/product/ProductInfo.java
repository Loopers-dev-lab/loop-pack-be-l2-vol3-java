package com.loopers.application.product;

import com.loopers.domain.product.ProductModel;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record ProductInfo(Long id, String name, String description, BigDecimal price, Integer stock, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
    public static ProductInfo from(ProductModel model) {
        return new ProductInfo(
            model.getId(),
            model.getName(),
            model.getDescription(),
            model.getPrice(),
            model.getStock(),
            model.getCreatedAt(),
            model.getUpdatedAt()
        );
    }
}
