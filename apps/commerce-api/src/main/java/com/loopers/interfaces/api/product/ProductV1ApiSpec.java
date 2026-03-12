package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Product V1 API", description = "상품 관련 API입니다.")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 조회합니다. brandId로 필터링할 수 있습니다."
    )
    ApiResponse<Page<ProductV1Dto.ProductResponse>> getProducts(Long brandId, Pageable pageable);

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 상품 정보를 조회합니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> getProduct(Long productId);
}
