package com.loopers.interfaces.api.experiment;

import com.loopers.application.product.ProductExperimentInfo;
import com.loopers.application.product.ProductInfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductExperimentV1Dto {

    // Response

    public record DetailResponse(
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
        public static DetailResponse from(ProductExperimentInfo info) {
            return new DetailResponse(
                    info.id(),
                    info.brandId(),
                    info.brandName(),
                    info.name(),
                    info.price(),
                    info.stockQuantity(),
                    info.description(),
                    info.likeCount(),
                    info.liked(),
                    info.createdAt()
            );
        }
    }

    public record ListResponse(
            Long id,
            Long brandId,
            String brandName,
            String name,
            BigDecimal price,
            Integer likeCount,
            LocalDateTime createdAt
    ) {
        public static ListResponse from(ProductInfo info) {
            return new ListResponse(
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
