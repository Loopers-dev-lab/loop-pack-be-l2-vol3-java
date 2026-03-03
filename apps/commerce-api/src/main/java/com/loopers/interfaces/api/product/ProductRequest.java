package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductCommand;
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
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

public record ProductRequest() {

    // Command

    public record Register(
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
        public ProductCommand.Register toCommand() {
            return ProductCommand.Register.of(brandId, name, price, stockQuantity, description);
        }
    }

    public record UpdateInfo(
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
        public ProductCommand.UpdateInfo toCommand() {
            return ProductCommand.UpdateInfo.of(name, price, stockQuantity, description);
        }
    }

    // Query

    public record ListAll(
            String name,
            Long brandId,
            ProductStatus status,

            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public ListAll {
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

    public record ListActive(
            Long brandId,
            ProductSort sort,

            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public ListActive {
            if (sort == null) sort = ProductSort.RECENT;
            if (page == null) page = 0;
            if (size == null) size = 20;
        }

        public enum ProductSort {
            RECENT, PRICE_ASC, LIKES_DESC;

            public Sort toSort() {
                return switch (this) {
                    case RECENT -> Sort.by(Sort.Direction.DESC, "createdAt");
                    case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price");
                    case LIKES_DESC -> Sort.by(Sort.Direction.DESC, "likeCount");
                };
            }
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size, sort.toSort());
        }
    }
}
