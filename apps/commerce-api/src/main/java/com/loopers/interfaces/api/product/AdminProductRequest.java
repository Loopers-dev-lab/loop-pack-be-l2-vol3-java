package com.loopers.interfaces.api.product;

import com.loopers.domain.product.ProductStatus;

/** 상품 어드민 API 요청 DTO */
public class AdminProductRequest {

    public record CreateProductRequest(
            Long brandId,
            String name,
            String description,
            int basePrice,
            int quantity
    ) {}

    public record UpdateProductRequest(
            String name,
            String description,
            Integer basePrice
    ) {}

    public record ChangeStatusRequest(
            ProductStatus status
    ) {}
}
