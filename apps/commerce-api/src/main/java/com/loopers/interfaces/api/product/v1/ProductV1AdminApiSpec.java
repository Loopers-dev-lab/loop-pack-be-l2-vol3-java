package com.loopers.interfaces.api.product.v1;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 Admin API", description = "상품 어드민 API 입니다.")
public interface ProductV1AdminApiSpec {

    @Operation(
            summary = "상품 등록",
            description = "새로운 상품을 등록합니다."
    )
    ApiResponse<ProductDto.CreateProductResponse> createProduct(
            @Schema(description = "상품 등록 요청 정보")
            ProductDto.CreateProductRequest request
    );
}