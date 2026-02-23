package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand V1 API", description = "고객용 브랜드 관련 API 입니다.")
public interface BrandV1ApiSpec {

    @Operation(
            summary = "브랜드 상세 조회",
            description = "브랜드의 상세 정보를 조회합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> getBrandDetails(
            @Parameter(description = "브랜드 ID") long id
    );
}
