package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductUserV1Dto {

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
            Integer dailyRank,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ProductResponse from(ProductInfo info) {
            return from(info, null);
        }

        public static ProductResponse from(ProductInfo info, Integer dailyRank) {
            return new ProductResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.stockQuantity(),
                    info.description(),
                    info.likeCount(),
                    dailyRank,
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }
}
