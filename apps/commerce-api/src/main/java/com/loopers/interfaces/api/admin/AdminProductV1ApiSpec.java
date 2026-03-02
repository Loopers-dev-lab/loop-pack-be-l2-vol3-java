package com.loopers.interfaces.api.admin;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 어드민 상품 API 명세. (/api-admin/v1/products)
 */
@Tag(name = "Admin Product", description = "어드민 상품 관리 API")
public interface AdminProductV1ApiSpec {

    @Operation(summary = "상품 등록")
    ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> createProduct(
        @RequestBody AdminProductV1Dto.CreateProductRequest request
    );

    @Operation(summary = "상품 조회")
    ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> getProduct(
        @PathVariable Long productId
    );

    @Operation(summary = "상품 수정 (브랜드 변경 불가)")
    ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> updateProduct(
        @PathVariable Long productId,
        @RequestBody AdminProductV1Dto.UpdateProductRequest request
    );

    @Operation(summary = "상품 삭제 (soft)")
    ResponseEntity<ApiResponse<Void>> deleteProduct(
        @PathVariable Long productId
    );
}
