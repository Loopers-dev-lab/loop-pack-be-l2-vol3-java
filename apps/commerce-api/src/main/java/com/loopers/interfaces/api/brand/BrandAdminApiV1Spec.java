package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand Admin API", description = "브랜드 관리 API")
public interface BrandAdminApiV1Spec {

    @Operation(
            summary = "브랜드 등록",
            description = "새로운 입점 브랜드를 등록합니다."
    )
    ApiResponse<BrandAdminV1Dto.BrandResponse> register(
            BrandAdminV1Dto.RegisterRequest request
    );
}
