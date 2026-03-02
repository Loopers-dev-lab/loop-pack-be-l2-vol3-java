package com.loopers.interfaces.api.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
/**
 * 어드민 상품 API 전용 DTO. (interfaces 레이어, api-admin/v1)
 * 소속 브랜드는 등록 시에만 지정 가능, 수정 시 변경 불가 (01 §2.6).
 */
public final class AdminProductV1Dto {

    public record CreateProductRequest(
        @NotNull(message = "브랜드 ID는 필수입니다.")
        Long brandId,
        @NotBlank(message = "상품명은 비어 있을 수 없습니다.")
        String name,
        @NotNull(message = "가격은 필수입니다.")
        @DecimalMin(value = "0", message = "가격은 0 이상이어야 합니다.")
        BigDecimal price,
        @NotNull(message = "재고 수량은 필수입니다.")
        @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
        Integer stockQuantity
    ) {}

    public record UpdateProductRequest(
        @NotBlank(message = "상품명은 비어 있을 수 없습니다.")
        String name,
        @NotNull(message = "가격은 필수입니다.")
        @DecimalMin(value = "0", message = "가격은 0 이상이어야 합니다.")
        BigDecimal price,
        @NotNull(message = "재고 수량은 필수입니다.")
        @Min(value = 0, message = "재고 수량은 0 이상이어야 합니다.")
        Integer stockQuantity
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String name,
        BigDecimal price,
        int stockQuantity,
        boolean deleted
    ) {}
}
