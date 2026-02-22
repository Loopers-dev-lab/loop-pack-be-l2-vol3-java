package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandAdminFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 브랜드 관리 어드민 API 컨트롤러 (X-Loopers-Ldap 인증 필요) */
@RestController
@RequestMapping("/api-admin/v1/brands")
public class AdminBrandController implements AdminBrandApiSpec {

    private final BrandService brandService;
    private final BrandAdminFacade brandAdminFacade;

    public AdminBrandController(BrandService brandService, BrandAdminFacade brandAdminFacade) {
        this.brandService = brandService;
        this.brandAdminFacade = brandAdminFacade;
    }

    /** 전체 브랜드 목록 페이지네이션 조회 */
    @GetMapping
    @Override
    public ApiResponse<AdminBrandResponse.BrandListResponse> getBrands(
            @AuthAdmin String ldap,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<Brand> brands = this.brandService.getAllBrands(page, size);
        long totalElements = this.brandService.countAllBrands();
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        List<AdminBrandResponse.BrandDetail> brandDetails = brands.stream()
                .map(BrandInfo::from)
                .map(AdminBrandResponse.BrandDetail::from)
                .toList();

        return ApiResponse.success(new AdminBrandResponse.BrandListResponse(
                brandDetails, page, size, totalElements, totalPages));
    }

    /** 브랜드 상세 조회 (BrandAdminFacade → 브랜드 + 전체 상품 목록) */
    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<AdminBrandResponse.BrandDetailWithProducts> getBrand(
            @AuthAdmin String ldap,
            @PathVariable Long brandId
    ) {
        BrandAdminFacade.BrandAdminDetailResult result = brandAdminFacade.getBrandDetail(brandId);
        return ApiResponse.success(
                AdminBrandResponse.BrandDetailWithProducts.from(result.brand(), result.products()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<AdminBrandResponse.BrandDetail> createBrand(
            @AuthAdmin String ldap,
            @RequestBody AdminBrandRequest.CreateBrandRequest request
    ) {
        Brand brand = this.brandService.create(request.name(), request.description());
        return ApiResponse.success(AdminBrandResponse.BrandDetail.from(BrandInfo.from(brand)));
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<AdminBrandResponse.BrandDetail> updateBrand(
            @AuthAdmin String ldap,
            @PathVariable Long brandId,
            @RequestBody AdminBrandRequest.UpdateBrandRequest request
    ) {
        Brand brand = this.brandService.update(brandId, request.name(), request.description());
        return ApiResponse.success(AdminBrandResponse.BrandDetail.from(BrandInfo.from(brand)));
    }

    @PatchMapping("/{brandId}/status")
    @Override
    public ApiResponse<AdminBrandResponse.BrandDetail> changeBrandStatus(
            @AuthAdmin String ldap,
            @PathVariable Long brandId,
            @RequestBody AdminBrandRequest.ChangeStatusRequest request
    ) {
        Brand brand = this.brandService.changeStatus(brandId, request.status());
        return ApiResponse.success(AdminBrandResponse.BrandDetail.from(BrandInfo.from(brand)));
    }

    /** 브랜드 삭제 (BrandAdminFacade → 상품/재고 연쇄 삭제) */
    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Void> deleteBrand(
            @AuthAdmin String ldap,
            @PathVariable Long brandId
    ) {
        brandAdminFacade.deleteBrand(brandId);
        return ApiResponse.success(null);
    }
}
