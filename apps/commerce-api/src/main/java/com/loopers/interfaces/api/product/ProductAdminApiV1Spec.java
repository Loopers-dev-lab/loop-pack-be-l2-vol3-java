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

    @Operation(
            summary = "상품 정보 수정",
            description = "등록된 상품의 정보를 수정합니다. 소속 브랜드는 변경할 수 없습니다."
    )
    ApiResponse<ProductAdminV1Dto.ProductResponse> update(
            Long productId,
            ProductAdminV1Dto.UpdateRequest request
    );

    @Operation(
            summary = "상품 삭제",
            description = "상품을 삭제합니다."
    )
    ApiResponse<Void> delete(Long productId);
}
