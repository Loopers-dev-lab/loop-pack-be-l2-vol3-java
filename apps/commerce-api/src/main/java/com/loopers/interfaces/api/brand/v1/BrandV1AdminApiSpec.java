package com.loopers.interfaces.api.brand.v1;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.brand.v1.BrandDto.BrandResponse;
import com.loopers.interfaces.api.brand.v1.BrandDto.UpdateBrandRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand V1 Admin API", description = "브랜드 어드민 API 입니다.")
public interface BrandV1AdminApiSpec {

    @Operation(
            summary = "브랜드 등록",
            description = "새로운 브랜드를 등록합니다."
    )
    ApiResponse<BrandDto.CreateBrandResponse> createBrand(
            @Schema(description = "브랜드 등록 요청 정보")
            BrandDto.CreateBrandRequest request
    );

    @Operation(
            summary = "브랜드 목록 조회",
            description = "등록된 브랜드 목록을 조회합니다."
    )
    ApiResponse<PageResponse<BrandResponse>> getBrands(int page, int size);

    @Operation(
            summary = "브랜드 상세 조회",
            description = "특정 브랜드의 상세 정보를 조회합니다."
    )
    ApiResponse<BrandResponse> getBrand(Long brandId);

    @Operation(
            summary = "브랜드 정보 수정",
            description = "특정 브랜드의 정보를 수정합니다."
    )
    ApiResponse<Object> updateBrand(Long brandId, UpdateBrandRequest request);

    @Operation(
            summary = "브랜드 삭제",
            description = "브랜드를 삭제합니다. 소속 상품과 해당 상품의 좋아요도 함께 삭제됩니다."
    )
    ApiResponse<Object> deleteBrand(Long brandId);
}
