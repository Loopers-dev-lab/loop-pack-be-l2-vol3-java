package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Brand Admin V1 API", description = "브랜드 관리 API입니다.")
public interface BrandAdminV1ApiSpec {

    @Operation(
        summary = "브랜드 목록 조회",
        description = "모든 브랜드 목록을 조회합니다."
    )
    ApiResponse<List<BrandV1Dto.BrandResponse>> getAllBrands();

    @Operation(
        summary = "브랜드 상세 조회",
        description = "브랜드 ID로 브랜드 정보를 조회합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> getBrand(Long brandId);

    @Operation(
        summary = "브랜드 등록",
        description = "새로운 브랜드를 등록합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> createBrand(BrandV1Dto.BrandCreateRequest request);

    @Operation(
        summary = "브랜드 수정",
        description = "브랜드 정보를 수정합니다."
    )
    ApiResponse<BrandV1Dto.BrandResponse> updateBrand(Long brandId, BrandV1Dto.BrandUpdateRequest request);

    @Operation(
        summary = "브랜드 삭제",
        description = "브랜드를 삭제합니다. (Soft Delete)"
    )
    ApiResponse<Object> deleteBrand(Long brandId);
}
