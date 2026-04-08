package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;

@Tag(name = "Product V1 API", description = "고객용 상품 조회 API (비로그인 허용)")
public interface ProductV1ApiSpec {

    @Operation(
        summary = "상품 목록 조회",
        description = "정렬·페이징·브랜드 필터로 상품 목록을 조회합니다. sort: latest(기본), price_asc, price_desc, likes_desc"
    )
    ResponseEntity<ApiResponse<ProductV1Dto.ListResponse>> getProductList(
        @Parameter(description = "브랜드 ID (선택)") Long brandId,
        @Parameter(description = "정렬 기준") String sort,
        @Parameter(description = "페이지 (0부터)") @Min(0) int page,
        @Parameter(description = "페이지 크기") @Min(0) int size
    );

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 ID로 상세 정보를 조회합니다. 브랜드명·좋아요 수·일간 랭킹 순위(선택 일자) 포함. 로그인 없이 조회 가능."
    )
    ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> getProductDetail(
        @Parameter(description = "상품 ID", required = true)
        Long productId,
        @Parameter(description = "랭킹 기준 일자 yyyyMMdd (생략 시 오늘, Asia/Seoul)")
        String date
    );
}
