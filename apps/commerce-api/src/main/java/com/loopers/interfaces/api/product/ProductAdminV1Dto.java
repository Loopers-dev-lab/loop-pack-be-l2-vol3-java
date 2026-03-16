package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductPageInfo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.List;

public class ProductAdminV1Dto {

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

    public record RegisterRequest(
        @NotNull(message = "브랜드 ID는 필수입니다.")
        Long brandId,

        @NotBlank(message = "상품 이름은 필수입니다.")
        String name,

        @NotNull(message = "상품 가격은 필수입니다.")
        @Min(value = 0, message = "상품 가격은 0 이상이어야 합니다.")
        Long price,

        String description,

        @NotNull(message = "재고 수량은 필수입니다.")
        @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
        Integer stockQuantity,

        @NotBlank(message = "상품 상태는 필수입니다.")
        String status
    ) {}

    public record UpdateRequest(
        @NotNull(message = "브랜드 ID는 필수입니다.")
        Long brandId,

        @NotBlank(message = "상품 이름은 필수입니다.")
        String name,

        @NotNull(message = "상품 가격은 필수입니다.")
        @Min(value = 0, message = "상품 가격은 0 이상이어야 합니다.")
        Long price,

        String description,

        @NotNull(message = "재고 수량은 필수입니다.")
        @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
        Integer stockQuantity,

        @NotBlank(message = "상품 상태는 필수입니다.")
        String status
    ) {}

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
        ZonedDateTime updatedAt
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
                info.updatedAt()
            );
        }
    }
}
