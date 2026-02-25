package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 상품 관리 어드민 API 명세 (X-Loopers-Ldap 인증 필요) */
@Tag(name = "Admin Product API", description = "상품 관리 어드민 API")
public interface AdminProductApiSpec {

    @Operation(summary = "상품 목록 조회", description = "전체 상품 목록을 페이지네이션으로 조회합니다.")
    ApiResponse<AdminProductResponse.ProductListResponse> getProducts(
            String ldap, int page, int size, Long brandId);

    @Operation(summary = "상품 상세 조회", description = "상품 상세 정보를 조회합니다.")
    ApiResponse<AdminProductResponse.ProductDetail> getProduct(String ldap, Long productId);

    @Operation(summary = "상품 등록", description = "새로운 상품을 등록합니다.")
    ApiResponse<AdminProductResponse.ProductDetail> createProduct(
            String ldap, AdminProductRequest.CreateProductRequest request);

    @Operation(summary = "상품 부분 수정", description = "상품 정보를 부분 수정합니다. 전달된 필드만 수정되며, null인 필드는 기존값을 유지합니다.")
    ApiResponse<AdminProductResponse.ProductDetail> updateProduct(
            String ldap, Long productId, AdminProductRequest.UpdateProductRequest request);

    @Operation(summary = "상품 상태 변경", description = "상품 상태를 변경합니다.")
    ApiResponse<AdminProductResponse.ProductDetail> changeProductStatus(
            String ldap, Long productId, AdminProductRequest.ChangeStatusRequest request);

    @Operation(summary = "상품 삭제", description = "상품을 삭제합니다. (소프트 삭제, 재고 연쇄 삭제)")
    ApiResponse<Void> deleteProduct(String ldap, Long productId);
}
