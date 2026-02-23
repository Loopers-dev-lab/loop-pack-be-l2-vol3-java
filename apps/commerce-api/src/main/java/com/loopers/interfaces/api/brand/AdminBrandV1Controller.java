package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.application.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.brand.dto.BrandV1Dto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class AdminBrandV1Controller {

    private final BrandService brandService;

    @GetMapping
    public ApiResponse<PageResponse<BrandV1Dto.AdminBrandResponse>> getBrands(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<BrandV1Dto.AdminBrandResponse> page = brandService.getBrands(pageable)
                                                               .map(BrandV1Dto.AdminBrandResponse::from);
        return ApiResponse.success(PageResponse.from(page));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.AdminBrandResponse> getBrand(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long brandId
    ) {
        Brand brand = brandService.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.AdminBrandResponse.from(brand));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandV1Dto.AdminBrandResponse> createBrand(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @Valid @RequestBody BrandV1Dto.CreateRequest request
    ) {
        Brand brand = brandService.register(request.name(), request.description());
        return ApiResponse.success(BrandV1Dto.AdminBrandResponse.from(brand));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandV1Dto.AdminBrandResponse> updateBrand(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long brandId,
            @Valid @RequestBody BrandV1Dto.UpdateRequest request
    ) {
        Brand brand = brandService.update(brandId, request.name(), request.description());
        return ApiResponse.success(BrandV1Dto.AdminBrandResponse.from(brand));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(
            @RequestHeader("X-Loopers-Ldap") String ldap,
            @PathVariable Long brandId
    ) {
        brandService.delete(brandId);
        return ApiResponse.success(null);
    }
}
