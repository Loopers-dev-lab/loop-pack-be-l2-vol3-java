package com.loopers.interfaces.api.product;

import com.loopers.domain.product.ProductSortType;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Product API", description = "상품 관련 고객 API")
public interface ProductApiSpec {

    @Operation(summary = "상품 목록 조회", description = "노출 가능한 상품 목록을 페이지네이션으로 조회합니다.")
    ApiResponse<ProductResponse.ProductListResponse> getProducts(
            Long brandId, ProductSortType sort, int page, int size);

    @Operation(summary = "상품 상세 조회", description = "상품 상세 정보를 조회합니다.")
    ApiResponse<ProductResponse.ProductDetail> getProduct(Long productId);
}
