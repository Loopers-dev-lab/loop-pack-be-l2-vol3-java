package com.loopers.interfaces.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.product.dto.AdminProductV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Product V1 API", description = "상품 관리자 API 입니다.")
public interface AdminProductV1ApiSpec {

    @Operation(
        summary = "상품 등록",
        description = "새로운 상품을 등록합니다."
    )
    ApiResponse<Object> register(
        @RequestBody(description = "상품 등록 요청 정보")
        AdminProductV1Dto.RegisterRequest request
    );

    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 페이지네이션으로 조회합니다."
    )
    ApiResponse<AdminProductV1Dto.ListResponse> list(
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        int page,
        @Parameter(description = "페이지 크기", example = "20")
        int size,
        @Parameter(description = "브랜드 ID (선택)", example = "1")
        Long brandId
    );

    @Operation(
        summary = "상품 상세 조회",
        description = "특정 상품의 상세 정보를 조회합니다."
    )
    ApiResponse<AdminProductV1Dto.DetailResponse> getById(
        @Parameter(description = "상품 ID", required = true, example = "1")
        Long productId
    );

    @Operation(
        summary = "상품 수정",
        description = "상품 정보를 수정합니다."
    )
    ApiResponse<Object> update(
        @Parameter(description = "상품 ID", required = true, example = "1")
        Long productId,
        @RequestBody(description = "상품 수정 요청 정보")
        AdminProductV1Dto.UpdateRequest request
    );

    @Operation(
        summary = "상품 삭제",
        description = "상품을 삭제합니다."
    )
    ApiResponse<Object> delete(
        @Parameter(description = "상품 ID", required = true, example = "1")
        Long productId
    );
}
