package com.loopers.interfaces.brand;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.brand.dto.BrandV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand V1 API", description = "브랜드 API 입니다.")
public interface BrandV1ApiSpec {

    @Operation(
        summary = "브랜드 상세 조회",
        description = "특정 브랜드의 정보를 조회합니다."
    )
    ApiResponse<BrandV1Dto.DetailResponse> getById(
        @Parameter(description = "브랜드 ID", required = true, example = "1")
        Long brandId
    );
}
