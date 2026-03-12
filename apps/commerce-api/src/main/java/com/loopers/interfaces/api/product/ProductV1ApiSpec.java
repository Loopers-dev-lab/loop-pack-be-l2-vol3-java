package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Product V1 API", description = "고객 상품 API")
public interface ProductV1ApiSpec {

    @Operation(summary = "상품 목록 조회", description = "상품 목록을 페이징하여 조회합니다.")
    ApiResponse<Page<ProductV1Dto.ProductSummaryResponse>> getAll(Pageable pageable, String sortType);

    @Operation(summary = "상품 상세 조회", description = "상품 상세 정보를 조회합니다.")
    ApiResponse<ProductV1Dto.ProductDetailResponse> getById(Long productId);
}
