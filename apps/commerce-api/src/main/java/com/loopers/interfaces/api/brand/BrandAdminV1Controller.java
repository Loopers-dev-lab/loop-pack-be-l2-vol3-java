package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AdminUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandFacade brandFacade;

    @PostMapping
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> create(
        @AdminUser String adminLdap,
        @RequestBody BrandAdminV1Dto.CreateRequest request
    ) {
        BrandInfo info = brandFacade.register(request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @GetMapping
    @Override
    public ApiResponse<Page<BrandAdminV1Dto.BrandResponse>> getAll(
        @AdminUser String adminLdap,
        Pageable pageable
    ) {
        Page<BrandAdminV1Dto.BrandResponse> response = brandFacade.getAll(pageable)
            .map(BrandAdminV1Dto.BrandResponse::from);
        return ApiResponse.success(response);
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> getById(
        @AdminUser String adminLdap,
        @PathVariable(value = "brandId") Long brandId
    ) {
        BrandInfo info = brandFacade.getById(brandId);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<BrandAdminV1Dto.BrandResponse> update(
        @AdminUser String adminLdap,
        @PathVariable(value = "brandId") Long brandId,
        @RequestBody BrandAdminV1Dto.UpdateRequest request
    ) {
        BrandInfo info = brandFacade.update(brandId, request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(info));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Object> delete(
        @AdminUser String adminLdap,
        @PathVariable(value = "brandId") Long brandId
    ) {
        brandFacade.delete(brandId);
        return ApiResponse.success();
    }
}
