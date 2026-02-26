package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeProductInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class LikeV1Dto {

    // Response

    public record LikeProductResponse(
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
        public static LikeProductResponse from(LikeProductInfo info) {
            return new LikeProductResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.stockQuantity(),
                    info.description(),
                    info.likeCount(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }
}
