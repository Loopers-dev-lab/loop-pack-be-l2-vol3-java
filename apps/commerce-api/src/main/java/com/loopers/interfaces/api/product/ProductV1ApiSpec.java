package com.loopers.interfaces.api.product;

import com.loopers.domain.product.SortCondition;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Product V1 API", description = "상품 API")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 목록 조회", description = "정렬 조건에 따라 상품 목록을 조회합니다.")
    ApiResponse<List<ProductV1Dto.ProductListResponse>> getProductList(
        @Parameter(description = "정렬 조건: latest, price_asc, likes_desc") SortCondition sort
    );

    @Operation(summary = "상품 상세 조회", description = "ID로 상품 상세 정보를 조회합니다.")
    ApiResponse<ProductV1Dto.ProductDetailResponse> getProductDetail(Long productId);
}
