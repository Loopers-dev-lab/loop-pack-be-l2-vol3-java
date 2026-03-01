package com.loopers.interfaces.api.product.v1;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product V1 Admin API", description = "상품 어드민 API 입니다.")
public interface ProductV1AdminApiSpec {

    @Operation(
            summary = "상품 등록",
            description = "새로운 상품을 등록합니다."
    )
    ApiResponse<ProductDto.CreateProductResponse> createProduct(
            @Schema(description = "상품 등록 요청 정보")
            ProductDto.CreateProductRequest request
    );

    @Operation(
            summary = "상품 목록 조회",
            description = "등록된 상품들의 목록을 조회합니다."
    )
    ApiResponse<PageResponse<ProductDto.ProductResponse>> getProducts(
            Long brandId,
            int page,
            int size
    );

    @Operation(
            summary = "상품 상세 조회",
            description = "특정 상품의 상세 정보를 조회합니다."
    )
    ApiResponse<ProductDto.ProductResponse> getProduct(Long productId);

    @Operation(
            summary = "상품 정보 수정",
            description = "상품 정보를 수정합니다."
    )
    ApiResponse<Object> updateProduct(Long productId, ProductDto.UpdateProductRequest request);

    @Operation(
            summary = "상품 삭제",
            description = "상품을 삭제합니다. 해당 상품에 등록된 좋아요도 함께 삭제됩니다."
    )
    ApiResponse<Object> deleteProduct(Long productId);
}
