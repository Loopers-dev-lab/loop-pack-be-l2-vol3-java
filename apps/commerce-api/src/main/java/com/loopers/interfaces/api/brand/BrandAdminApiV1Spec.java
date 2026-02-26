package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Brand Admin API", description = "브랜드 관리 API")
public interface BrandAdminApiV1Spec {

    // Command

    @Operation(
            summary = "브랜드 등록",
            description = "새로운 입점 브랜드를 등록합니다."
    )
    ApiResponse<BrandAdminV1Dto.BrandResponse> register(
            BrandRequest.Register request
    );

    @Operation(
            summary = "브랜드 수정",
            description = "브랜드 정보를 수정합니다."
    )
    ApiResponse<BrandAdminV1Dto.BrandResponse> update(
            Long brandId,
            BrandRequest.Update request
    );

    @Operation(
            summary = "브랜드 삭제",
            description = "브랜드를 삭제합니다."
    )
    ApiResponse<Void> delete(Long brandId);

    // Query

    @Operation(
            summary = "브랜드 목록 조회",
            description = "브랜드 목록을 검색 조건과 함께 페이징 조회합니다."
    )
    ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>> list(
            BrandRequest.ListAll request
    );

    @Operation(
            summary = "브랜드 상세 조회",
            description = "브랜드 상세 정보를 조회합니다."
    )
    ApiResponse<BrandAdminV1Dto.BrandResponse> detail(Long brandId);
}
