package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Product V1 API", description = "고객용 상품 조회 API (비로그인 허용)")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 상세 정보를 조회합니다. 브랜드명·좋아요 수 포함. 로그인 없이 조회 가능."
    )
    ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> getProductDetail(
        @Parameter(description = "상품 ID", required = true)
        Long productId
    );
}
