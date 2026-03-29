package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Products", description = "상품 API")
public interface ProductsV1ApiSpec {

    @Operation(summary = "상품 목록 조회", description = "브랜드 필터, 정렬 조건으로 상품 목록을 페이지네이션으로 조회합니다.")
    @GetMapping("")
    ApiResponse<ProductV1Dto.PageResponse<ProductV1Dto.ProductListItemResponse>> getProductList(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "latest") String sort,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    );

    @Operation(summary = "상품 상세 조회", description = "상품 ID로 상품 상세 정보를 조회합니다.")
    @GetMapping("/{productId}")
    ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @RequestHeader(value = "User-Agent", required = false) String userAgent
    );
}
