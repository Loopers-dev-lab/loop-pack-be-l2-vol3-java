package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductAdminV1Dto {

    // Response

    public record ProductResponse(
            Long id,
            Long brandId,
            String brandName,
            String name,
            BigDecimal price,
            Integer stockQuantity,
            String description,
            Integer likeCount,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.stockQuantity(),
                    info.description(),
                    info.likeCount(),
                    info.status().name(),
                    info.createdAt(),
                    info.updatedAt(),
                    info.deletedAt()
            );
        }
    }
}
