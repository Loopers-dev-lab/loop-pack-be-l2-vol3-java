package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Product Admin V1 API", description = "상품 관리 API")
public interface ProductAdminV1ApiSpec {

    @Operation(summary = "상품 등록", description = "새로운 상품을 등록합니다.")
    ApiResponse<ProductAdminV1Dto.ProductAdminDetailResponse> create(String adminLdap, ProductAdminV1Dto.CreateRequest request);

    @Operation(summary = "상품 목록 조회 (관리자)", description = "관리자용 상품 목록을 조회합니다.")
    ApiResponse<Page<ProductAdminV1Dto.ProductAdminSummaryResponse>> getAll(String adminLdap, Pageable pageable, String sortType);

    @Operation(summary = "상품 상세 조회 (관리자)", description = "관리자용 상품 상세 정보를 조회합니다.")
    ApiResponse<ProductAdminV1Dto.ProductAdminDetailResponse> getById(String adminLdap, Long productId);

    @Operation(summary = "상품 수정", description = "상품 정보를 수정합니다.")
    ApiResponse<ProductAdminV1Dto.ProductAdminDetailResponse> update(String adminLdap, Long productId, ProductAdminV1Dto.UpdateRequest request);

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다.")
    ApiResponse<Object> delete(String adminLdap, Long productId);

    @Operation(summary = "재고 수정", description = "상품 재고를 수정합니다.")
    ApiResponse<ProductAdminV1Dto.ProductAdminDetailResponse> updateStock(String adminLdap, Long productId, ProductAdminV1Dto.UpdateStockRequest request);
}
