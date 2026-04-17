package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "Product User API", description = "상품 사용자 API")
public interface ProductUserApiV1Spec {

    // Query

    @Operation(
            summary = "상품 목록 조회",
            description = "활성 상품을 정렬/필터링하여 페이징 조회합니다."
    )
    ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>> list(
            ProductRequest.ListActive request
    );

    @Operation(
            summary = "상품 상세 조회",
            description = "활성 상품의 상세 정보를 조회합니다."
    )
    ApiResponse<ProductUserV1Dto.ProductResponse> detail(
            Long productId,
            String userAgent,
            Long userId,
            @Parameter(hidden = true) HttpServletRequest request
    );
}
