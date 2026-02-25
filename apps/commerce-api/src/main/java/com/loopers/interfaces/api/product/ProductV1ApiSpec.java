package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Product V1 API", description = "상품 공개 API 입니다.")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 페이징하여 조회합니다. 정렬: latest, price_asc, likes_desc"
    )
    ApiResponse<Page<ProductV1Dto.ProductResponse>> getAll(
        Pageable pageable,
        @Parameter(description = "정렬 조건 (latest, price_asc, likes_desc)", example = "latest") String sort
    );

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 상세 정보를 조회합니다. 브랜드 정보와 좋아요 수를 포함합니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> getProduct(
        @Parameter(description = "상품 ID", required = true) Long productId
    );
}
