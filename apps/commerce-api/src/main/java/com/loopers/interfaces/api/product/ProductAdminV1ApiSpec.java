package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "ProductAdmin V1 API", description = "관리자용 상품 관련 API 입니다.")
public interface ProductAdminV1ApiSpec {

    @Operation(summary = "상품 등록", description = "새로운 상품을 등록합니다. 상품은 이미 등록된 브랜드에 속해야 합니다.")
    ApiResponse<ProductAdminV1Dto.ProductResponse> registerProduct(ProductAdminV1Dto.RegisterRequest request);

    @Operation(summary = "상품 상세 조회", description = "상품의 상세 정보를 조회합니다.")
    ApiResponse<ProductAdminV1Dto.ProductResponse> getProductDetails(@Parameter(description = "상품 ID") long id);

    @Operation(summary = "상품 목록 조회", description = "등록된 상품 목록을 조회합니다. 브랜드 필터링과 정렬을 지원합니다.")
    ApiResponse<ProductAdminV1Dto.ProductListResponse> getProducts(
            @Parameter(description = "브랜드 ID (선택)") Long brandId,
            @Parameter(description = "정렬 기준 (기본값: latest). 허용값: latest(최신순), price_asc(가격 오름차순), likes_desc(좋아요 많은 순). 허용값 외 입력 시 400 에러") String sort,
            @Parameter(description = "페이지 번호 (0부터 시작)") int page,
            @Parameter(description = "페이지 크기") int size
    );

    @Operation(summary = "상품 정보 수정", description = "상품 정보를 수정합니다. 소속 브랜드는 변경할 수 없습니다.")
    ApiResponse<ProductAdminV1Dto.ProductResponse> updateProduct(
            @Parameter(description = "상품 ID") long id,
            ProductAdminV1Dto.UpdateRequest request
    );

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다.")
    ApiResponse<Void> deleteProduct(@Parameter(description = "상품 ID") long id);
}
