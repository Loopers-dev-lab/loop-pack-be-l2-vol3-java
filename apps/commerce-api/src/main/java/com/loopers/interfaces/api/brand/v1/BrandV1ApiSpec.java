package com.loopers.interfaces.api.brand.v1;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand V1 API", description = "브랜드 API 입니다.")
public interface BrandV1ApiSpec {

    @Operation(
            summary = "브랜드 상세 조회 API",
            description = "활성화된 브랜드의 상세 정보를 조회하는 API입니다."
    )
    ApiResponse<BrandDto.BrandResponse> getActiveBrand(Long brandId);
}
