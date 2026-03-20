package com.loopers.interfaces.api.product;

import com.loopers.domain.product.ProductSort;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Product V1 API", description = "상품 관련 API입니다.")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 목록 조회",
        description = "상품 목록을 조회합니다. brandId로 필터링하고, sort로 정렬할 수 있습니다."
    )
    ApiResponse<Page<ProductV1Dto.ProductResponse>> getProducts(
        @Parameter(description = "브랜드 ID (선택)") Long brandId,
        @Parameter(description = "정렬 기준: LATEST(최신순), PRICE_ASC(가격 낮은 순), LIKES_DESC(좋아요 많은 순)") ProductSort sort,
        Pageable pageable
    );

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 상품 정보를 조회합니다. 좋아요 수가 포함됩니다."
    )
    ApiResponse<ProductV1Dto.ProductResponse> getProduct(Long productId);
}
