package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 API", description = "상품 관리 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 생성",
        description = "새로운 상품을 생성합니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> createProduct(
        ProductV1Dto.CreateRequest request
    );

    @Operation(
        summary = "상품 조회",
        description = "ID로 상품을 조회합니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @Schema(description = "상품 ID")
        Long productId
    );

    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 페이징하여 조회합니다."
    )
    ApiResponse<ProductV1Dto.ProductListResponse> getProducts(
        @Schema(description = "페이지 번호")
        int page,
        @Schema(description = "페이지 크기")
        int size
    );

    @Operation(
        summary = "상품 수정",
        description = "상품 정보를 수정합니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> updateProduct(
        @Schema(description = "상품 ID")
        Long productId,
        ProductV1Dto.UpdateRequest request
    );

    @Operation(
        summary = "상품 삭제",
        description = "상품을 삭제합니다."
    )
    ApiResponse<Object> deleteProduct(
        @Schema(description = "상품 ID")
        Long productId
    );
}
