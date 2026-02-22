package com.loopers.interfaces.api.product.v1;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API", description = "대고객 상품 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(
            summary = "상품 상세 조회 API",
            description = "상품 상세 조회 API 입니다."
    )
    ApiResponse<ProductDto.ProductDetailResponse> getActiveProduct(Long userId, Long productId);
}
