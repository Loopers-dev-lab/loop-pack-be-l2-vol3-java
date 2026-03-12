package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductResult;
import com.loopers.domain.product.ProductInfo;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;

public class ProductV1Dto {

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        String description,
        Long price,
        Integer stock,
        String imageUrl,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static ProductResponse from(ProductResult result) {
            return new ProductResponse(
                result.id(),
                result.brandId(),
                result.name(),
                result.description(),
                result.price(),
                result.stock(),
                result.imageUrl(),
                result.createdAt(),
                result.updatedAt()
            );
        }
    }

    public record ProductCreateRequest(
        @NotNull(message = "브랜드 ID는 필수입니다.")
        Long brandId,

        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 200, message = "상품명은 200자를 초과할 수 없습니다.")
        String name,

        @Size(max = 2000, message = "상품 설명은 2000자를 초과할 수 없습니다.")
        String description,

        @NotNull(message = "가격은 필수입니다.")
        @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
        Long price,

        @NotNull(message = "재고는 필수입니다.")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        Integer stock,

        @Size(max = 500, message = "이미지 URL은 500자를 초과할 수 없습니다.")
        String imageUrl
    ) {
        public ProductInfo toInfo() {
            return new ProductInfo(brandId, name, description, price, stock, imageUrl);
        }
    }

    public record ProductUpdateRequest(
        @NotNull(message = "브랜드 ID는 필수입니다.")
        Long brandId,

        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 200, message = "상품명은 200자를 초과할 수 없습니다.")
        String name,

        @Size(max = 2000, message = "상품 설명은 2000자를 초과할 수 없습니다.")
        String description,

        @NotNull(message = "가격은 필수입니다.")
        @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
        Long price,

        @NotNull(message = "재고는 필수입니다.")
        @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
        Integer stock,

        @Size(max = 500, message = "이미지 URL은 500자를 초과할 수 없습니다.")
        String imageUrl
    ) {
        public ProductInfo toInfo() {
            return new ProductInfo(brandId, name, description, price, stock, imageUrl);
        }
    }
}
