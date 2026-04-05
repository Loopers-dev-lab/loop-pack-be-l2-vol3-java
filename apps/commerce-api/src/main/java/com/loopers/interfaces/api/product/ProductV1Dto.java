package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.common.cursor.CursorPageResult;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public class ProductV1Dto {

    public record CreateProductRequest(
            @NotBlank(message = "상품 ID는 필수입니다")
            @Pattern(regexp = "^[A-Za-z0-9]{1,20}$", message = "상품 ID는 영문+숫자, 1~20자여야 합니다")
            String productId,

            @NotBlank(message = "브랜드 ID는 필수입니다")
            @Pattern(regexp = "^[A-Za-z0-9]{1,10}$", message = "브랜드 ID는 영문+숫자, 1~10자여야 합니다")
            String brandId,

            @NotBlank(message = "상품명은 필수입니다")
            @Size(min = 1, max = 100, message = "상품명은 1~100자여야 합니다")
            String productName,

            @NotNull(message = "가격은 필수입니다")
            @DecimalMin(value = "0.0", inclusive = true, message = "가격은 0 이상이어야 합니다")
            BigDecimal price,

            @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다")
            int stockQuantity
    ) {
    }

    public record ProductResponse(
            Long id,
            String productId,
            Long refBrandId,
            String productName,
            BigDecimal price,
            int stockQuantity,
            BrandInfo brand,
            long likesCount,
            Long rank,
            Double rankingScore
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                    info.id(),
                    info.productId(),
                    info.refBrandId(),
                    info.productName(),
                    info.price(),
                    info.stockQuantity(),
                    info.brand(),
                    info.likesCount(),
                    info.rank(),
                    info.rankingScore()
            );
        }
    }

    public record UpdateProductRequest(
            @NotBlank(message = "상품명은 필수입니다")
            @Size(min = 1, max = 100, message = "상품명은 1~100자여야 합니다")
            String productName,

            @NotNull(message = "가격은 필수입니다")
            @DecimalMin(value = "0.0", inclusive = true, message = "가격은 0 이상이어야 합니다")
            BigDecimal price,

            @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다")
            int stockQuantity
    ) {}

    public record ProductListResponse(
            java.util.List<ProductResponse> products,
            int currentPage,
            int pageSize,
            long totalElements,
            int totalPages
    ) {
        public static ProductListResponse from(org.springframework.data.domain.Page<ProductInfo> page) {
            return new ProductListResponse(
                    page.getContent().stream()
                            .map(ProductResponse::from)
                            .toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages()
            );
        }
    }

    public record CursorListResponse(
            List<ProductResponse> items,
            String nextCursor,
            boolean hasNext,
            int size
    ) {
        public static CursorListResponse from(CursorPageResult<ProductInfo> result) {
            return new CursorListResponse(
                    result.items().stream().map(ProductResponse::from).toList(),
                    result.nextCursor(),
                    result.hasNext(),
                    result.size()
            );
        }
    }
}
