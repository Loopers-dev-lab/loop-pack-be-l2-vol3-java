package com.loopers.interfaces.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.product.dto.ProductV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API", description = "상품 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 조회합니다. 브랜드 필터링, 정렬, 페이지네이션을 지원합니다."
    )
    ApiResponse<ProductV1Dto.ListResponse> list(
        @Parameter(description = "브랜드 ID (선택)", example = "1")
        Long brandId,
        @Parameter(description = "정렬 기준: latest / price_asc / likes_desc", example = "latest")
        String sort,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        int page,
        @Parameter(description = "페이지 크기", example = "20")
        int size
    );

    @Operation(
        summary = "상품 상세 조회",
        description = "특정 상품의 정보를 조회합니다."
    )
    ApiResponse<ProductV1Dto.DetailResponse> getById(
        @Parameter(description = "상품 ID", required = true, example = "1")
        Long productId
    );
}
