package com.loopers.interfaces.brand;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.brand.dto.AdminBrandV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Brand V1 API", description = "브랜드 관리자 API 입니다.")
public interface AdminBrandV1ApiSpec {

    @Operation(
        summary = "브랜드 등록",
        description = "새로운 브랜드를 등록합니다."
    )
    ApiResponse<Object> register(
        @RequestBody(description = "브랜드 등록 요청 정보")
        AdminBrandV1Dto.RegisterRequest request
    );

    @Operation(
        summary = "브랜드 목록 조회",
        description = "브랜드 목록을 페이지네이션으로 조회합니다."
    )
    ApiResponse<AdminBrandV1Dto.ListResponse> list(
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        int page,
        @Parameter(description = "페이지 크기", example = "20")
        int size
    );

    @Operation(
        summary = "브랜드 상세 조회",
        description = "특정 브랜드의 상세 정보를 조회합니다."
    )
    ApiResponse<AdminBrandV1Dto.DetailResponse> getById(
        @Parameter(description = "브랜드 ID", required = true, example = "1")
        Long brandId
    );

    @Operation(
        summary = "브랜드 수정",
        description = "브랜드 정보를 수정합니다."
    )
    ApiResponse<Object> update(
        @Parameter(description = "브랜드 ID", required = true, example = "1")
        Long brandId,
        @RequestBody(description = "브랜드 수정 요청 정보")
        AdminBrandV1Dto.UpdateRequest request
    );

    @Operation(
        summary = "브랜드 삭제",
        description = "브랜드를 삭제합니다."
    )
    ApiResponse<Object> delete(
        @Parameter(description = "브랜드 ID", required = true, example = "1")
        Long brandId
    );
}
