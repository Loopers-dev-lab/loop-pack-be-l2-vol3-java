package com.loopers.domain.model.product;

import java.time.LocalDateTime;

public record ProductData(
        Long id,
        Long brandId,
        ProductName name,
        Price price,
        Price salePrice,
        Stock stock,
        int likeCount,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
