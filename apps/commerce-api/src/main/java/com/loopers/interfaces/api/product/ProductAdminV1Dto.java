package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductAdminV1Dto {

    // Command

    public record RegisterRequest(
            @NotNull(message = "브랜드 ID는 필수입니다")
            Long brandId,

            @NotBlank(message = "상품명은 필수입니다")
            @Size(max = 200, message = "상품명은 200자 이하여야 합니다")
            String name,

            @NotNull(message = "가격은 필수입니다")
            @DecimalMin(value = "0", message = "가격은 0 이상이어야 합니다")
            @DecimalMax(value = "999999999", message = "가격은 999,999,999 이하여야 합니다")
            BigDecimal price,

            @NotNull(message = "재고 수량은 필수입니다")
            @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다")
            @Max(value = 9999999, message = "재고 수량은 9,999,999 이하여야 합니다")
            Integer stockQuantity,

            @Size(max = 1000, message = "상품 설명은 1,000자 이하여야 합니다")
            String description
    ) {
    }

    public record UpdateRequest(
            @Size(min = 1, max = 200, message = "상품명은 1~200자여야 합니다")
            String name,

            @DecimalMin(value = "0", message = "가격은 0 이상이어야 합니다")
            @DecimalMax(value = "999999999", message = "가격은 999,999,999 이하여야 합니다")
            BigDecimal price,

            @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다")
            @Max(value = 9999999, message = "재고 수량은 9,999,999 이하여야 합니다")
            Integer stockQuantity,

            @Size(max = 1000, message = "상품 설명은 1,000자 이하여야 합니다")
            String description
    ) {
    }

    // Query

    public record ListRequest(
            String name,
            Long brandId,
            ProductStatus status,

            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public ListRequest {
            if (page == null) page = 0;
            if (size == null) size = 20;
        }

        public enum ProductStatus {
            ACTIVE, DELETED;

            public Boolean toDeleted() {
                return this == DELETED ? Boolean.TRUE : Boolean.FALSE;
            }
        }

        public Boolean toDeleted() {
            return status != null ? status.toDeleted() : null;
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size);
        }
    }

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
