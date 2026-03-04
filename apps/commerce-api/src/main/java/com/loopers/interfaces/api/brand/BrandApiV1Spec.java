package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand API", description = "브랜드 사용자 API")
public interface BrandApiV1Spec {

    // Query

    @Operation(
            summary = "브랜드 목록 조회",
            description = "활성 브랜드 목록을 이름 오름차순으로 페이징 조회합니다."
    )
    ApiResponse<PageResponse<BrandV1Dto.BrandResponse>> list(
            BrandRequest.ListActive request
    );

    @Operation(
            summary = "브랜드 상세 조회",
            description = "활성 브랜드의 상세 정보를 조회합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> detail(Long brandId);
}
