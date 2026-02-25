package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 브랜드 관리 어드민 API 명세 (X-Loopers-Ldap 인증 필요) */
@Tag(name = "Admin Brand API", description = "브랜드 관리 어드민 API")
public interface AdminBrandApiSpec {

    @Operation(summary = "브랜드 목록 조회", description = "전체 브랜드 목록을 페이지네이션으로 조회합니다.")
    ApiResponse<AdminBrandResponse.BrandListResponse> getBrands(String ldap, int page, int size);

    @Operation(summary = "브랜드 상세 조회", description = "브랜드 상세 정보와 상품 목록을 조회합니다.")
    ApiResponse<AdminBrandResponse.BrandDetailWithProducts> getBrand(String ldap, Long brandId);

    @Operation(summary = "브랜드 등록", description = "새로운 브랜드를 등록합니다.")
    ApiResponse<AdminBrandResponse.BrandDetail> createBrand(String ldap, AdminBrandRequest.CreateBrandRequest request);

    @Operation(summary = "브랜드 부분 수정", description = "브랜드 정보를 부분 수정합니다. 전달된 필드만 수정되며, null인 필드는 기존값을 유지합니다.")
    ApiResponse<AdminBrandResponse.BrandDetail> updateBrand(String ldap, Long brandId, AdminBrandRequest.UpdateBrandRequest request);

    @Operation(summary = "브랜드 상태 변경", description = "브랜드 상태를 변경합니다.")
    ApiResponse<AdminBrandResponse.BrandDetail> changeBrandStatus(String ldap, Long brandId, AdminBrandRequest.ChangeStatusRequest request);

    @Operation(summary = "브랜드 삭제", description = "브랜드를 삭제합니다. (소프트 삭제, 상품/재고 연쇄 삭제)")
    ApiResponse<Void> deleteBrand(String ldap, Long brandId);
}
