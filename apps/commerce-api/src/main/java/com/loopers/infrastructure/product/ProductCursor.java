package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductModel;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record ProductCursor(
        String type,
        ZonedDateTime updatedAt,
        BigDecimal price,
        Long id
) {
    public static ProductCursor from(ProductModel last, String sortBy) {
        ProductCursorCondition condition = ProductCursorCondition.from(sortBy);
        return switch (condition) {
            case LATEST, LIKES_DESC -> new ProductCursor(condition.name(), last.getUpdatedAt(), null, last.getId());
            case PRICE_ASC -> new ProductCursor("PRICE_ASC", null, last.getPrice().value(), last.getId());
        };
    }
}