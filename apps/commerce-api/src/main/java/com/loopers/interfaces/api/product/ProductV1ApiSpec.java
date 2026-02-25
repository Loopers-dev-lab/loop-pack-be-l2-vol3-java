package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API", description = "상품 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 조회합니다. 브랜드별 필터링과 정렬이 가능합니다. "
            + "연관 데이터 삭제로 인해 content 수가 totalElements보다 적을 수 있습니다."
    )
    ApiResponse<ProductV1Dto.ProductPageResponse> getAll(Long brandId, String sort, int page, int size);

    @Operation(summary = "상품 정보 조회", description = "특정 상품의 정보를 조회합니다.")
    ApiResponse<ProductV1Dto.ProductResponse> getById(Long productId);
}
