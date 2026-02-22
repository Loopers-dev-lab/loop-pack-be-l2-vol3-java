package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Brand API", description = "브랜드 관련 고객 API")
public interface BrandApiSpec {

    @Operation(summary = "브랜드 목록 조회", description = "활성 상태의 브랜드 목록을 조회합니다.")
    ApiResponse<List<BrandResponse.BrandSummary>> getBrands();

    @Operation(summary = "브랜드 상세 조회", description = "브랜드 상세 정보와 상품 목록을 조회합니다.")
    ApiResponse<BrandResponse.BrandDetailWithProducts> getBrand(Long brandId);
}
