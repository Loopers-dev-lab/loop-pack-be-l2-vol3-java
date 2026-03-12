package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Brand Admin V1 API", description = "브랜드 관리 API")
public interface BrandAdminV1ApiSpec {

    @Operation(summary = "브랜드 등록", description = "새로운 브랜드를 등록합니다.")
    ApiResponse<BrandAdminV1Dto.BrandResponse> create(String adminLdap, BrandAdminV1Dto.CreateRequest request);

    @Operation(summary = "브랜드 목록 조회", description = "브랜드 목록을 페이징하여 조회합니다.")
    ApiResponse<Page<BrandAdminV1Dto.BrandResponse>> getAll(String adminLdap, Pageable pageable);

    @Operation(summary = "브랜드 상세 조회", description = "브랜드 상세 정보를 조회합니다.")
    ApiResponse<BrandAdminV1Dto.BrandResponse> getById(String adminLdap, Long brandId);

    @Operation(summary = "브랜드 수정", description = "브랜드 정보를 수정합니다.")
    ApiResponse<BrandAdminV1Dto.BrandResponse> update(String adminLdap, Long brandId, BrandAdminV1Dto.UpdateRequest request);

    @Operation(summary = "브랜드 삭제", description = "브랜드를 삭제합니다. 해당 브랜드의 상품도 함께 삭제됩니다.")
    ApiResponse<Object> delete(String adminLdap, Long brandId);
}
