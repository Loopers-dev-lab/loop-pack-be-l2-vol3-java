package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductPageInfo;
import jakarta.validation.constraints.Min;

import java.time.ZonedDateTime;
import java.util.List;

public class ProductV1Dto {

    public record DwellRequest(
        @Min(5) int dwellTimeSeconds
    ) {
    }

    public record ProductListResponse(
        List<ProductResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static ProductListResponse from(ProductPageInfo pageInfo) {
            List<ProductResponse> products = pageInfo.content().stream()
                .map(ProductResponse::from)
                .toList();
            return new ProductListResponse(
                products,
                pageInfo.page(),
                pageInfo.size(),
                pageInfo.totalElements(),
                pageInfo.totalPages()
            );
        }
    }

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        Long price,
        String description,
        int stockQuantity,
        String status,
        long likeCount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        Long ranking
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.description(),
                info.stockQuantity(),
                info.status(),
                info.likeCount(),
                info.createdAt(),
                info.updatedAt(),
                info.ranking()
            );
        }
    }
}
