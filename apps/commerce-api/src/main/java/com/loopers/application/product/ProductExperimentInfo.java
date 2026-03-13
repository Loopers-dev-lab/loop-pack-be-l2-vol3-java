package com.loopers.application.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductExperimentInfo(
        Long id,
        Long brandId,
        String brandName,
        String name,
        BigDecimal price,
        Integer stockQuantity,
        String description,
        Integer likeCount,
        boolean liked,
        LocalDateTime createdAt
) {
    public static ProductExperimentInfo from(ProductInfo info, boolean liked) {
        return new ProductExperimentInfo(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.stockQuantity(),
                info.description(),
                info.likeCount(),
                liked,
                info.createdAt()
        );
    }
}
