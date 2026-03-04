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
            Integer likeCount,
            LocalDateTime createdAt
    ) {
        public static LikeProductResponse from(LikeProductInfo info) {
            return new LikeProductResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.likeCount(),
                    info.createdAt()
            );
        }
    }
}
