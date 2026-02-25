package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product Admin API", description = "상품 관리 API")
public interface ProductAdminApiV1Spec {

    // Command

    @Operation(
            summary = "상품 등록",
            description = "특정 브랜드에 속한 신규 상품을 등록합니다."
    )
    ApiResponse<ProductAdminV1Dto.ProductResponse> register(
            ProductAdminV1Dto.RegisterRequest request
    );
}
