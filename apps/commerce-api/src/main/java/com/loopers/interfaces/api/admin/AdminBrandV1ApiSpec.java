package com.loopers.interfaces.api.admin;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 어드민 브랜드 API 명세. (/api-admin/v1/brands)
 */
@Tag(name = "Admin Brand", description = "어드민 브랜드 관리 API")
public interface AdminBrandV1ApiSpec {

    @Operation(summary = "브랜드 등록")
    ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> createBrand(
        @RequestBody AdminBrandV1Dto.CreateBrandRequest request
    );

    @Operation(summary = "브랜드 조회")
    ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> getBrand(
        @PathVariable Long brandId
    );

    @Operation(summary = "브랜드 수정")
    ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> renameBrand(
        @PathVariable Long brandId,
        @RequestBody AdminBrandV1Dto.UpdateBrandRequest request
    );

    @Operation(summary = "브랜드 삭제 (연쇄 삭제)")
    ResponseEntity<ApiResponse<Void>> deleteBrand(
        @PathVariable Long brandId
    );
}
