package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "BrandAdmin V1 API", description = "관리자용 브랜드 관련 API 입니다.")
public interface BrandAdminV1ApiSpec {

    @Operation(
            summary = "브랜드 등록",
            description = "관리자가 새로운 브랜드를 등록합니다."
    )
    ApiResponse<BrandAdminV1Dto.BrandResponse> registerBrand(BrandAdminV1Dto.RegisterRequest request);

    @Operation(
            summary = "브랜드 상세 조회",
            description = "브랜드의 상세 정보를 조회합니다."
    )
    ApiResponse<BrandAdminV1Dto.BrandResponse> getBrandDetails(
            @Parameter(description = "브랜드 ID") long id
    );

    @Operation(
            summary = "브랜드 목록 조회",
            description = "등록되어 있는 브랜드 목록을 조회합니다."
    )
    ApiResponse<BrandAdminV1Dto.BrandListResponse> getBrandList(
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 당 나타낼 데이터 개수", example = "10")
            @RequestParam(defaultValue = "10") int size
    );

    @Operation(
            summary = "브랜드 정보 수정",
            description = "브랜드 정보를 수정합니다."
    )
    ApiResponse<BrandAdminV1Dto.BrandResponse> updateBrand(
            @Parameter(description = "브랜드 ID") long id,
            BrandAdminV1Dto.UpdateRequest request
    );

    @Operation(
            summary = "브랜드 삭제",
            description = "브랜드를 삭제합니다."
    )
    ApiResponse<Void> deleteBrand(
            @Parameter(description = "브랜드 ID") long id
    );
}
