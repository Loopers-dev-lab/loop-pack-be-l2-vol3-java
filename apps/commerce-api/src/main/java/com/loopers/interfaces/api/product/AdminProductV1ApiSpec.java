package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Product V1 API", description = "어드민 상품 관리 API 입니다.")
public interface AdminProductV1ApiSpec {

    @Operation(summary = "상품 등록", description = "새로운 상품을 등록합니다. 상품의 브랜드는 이미 등록된 브랜드여야 합니다.")
    ApiResponse<AdminProductV1Dto.ProductResponse> create(AdminProductV1Dto.CreateRequest request);

    @Operation(summary = "상품 목록 조회", description = "상품 목록을 페이지 단위로 조회합니다. 브랜드별 필터링이 가능합니다.")
    ApiResponse<AdminProductV1Dto.ProductPageResponse> getAll(Long brandId, int page, int size);

    @Operation(summary = "상품 상세 조회", description = "특정 상품의 상세 정보를 조회합니다.")
    ApiResponse<AdminProductV1Dto.ProductResponse> getById(Long productId);

    @Operation(summary = "상품 수정", description = "상품 정보를 수정합니다. 상품의 브랜드는 수정할 수 없습니다.")
    ApiResponse<AdminProductV1Dto.ProductResponse> update(Long productId, AdminProductV1Dto.UpdateRequest request);

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다.")
    ApiResponse<Void> delete(Long productId);
}
