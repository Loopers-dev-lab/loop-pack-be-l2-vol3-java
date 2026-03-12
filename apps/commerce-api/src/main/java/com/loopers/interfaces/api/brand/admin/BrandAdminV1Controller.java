package com.loopers.interfaces.api.brand.admin;

import com.loopers.application.brand.BrandFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AdminInfo;
import com.loopers.interfaces.auth.AdminUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller {

    private final BrandService brandService;
    private final BrandFacade brandFacade;

    @PostMapping
    public ApiResponse<BrandAdminV1Dto.BrandResponse> create(
        @AdminUser AdminInfo admin,
        @RequestBody BrandAdminV1Dto.CreateRequest request
    ) {
        BrandModel brand = brandService.register(request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(brand));
    }

    @GetMapping
    public ApiResponse<Page<BrandAdminV1Dto.BrandResponse>> getAll(
        @AdminUser AdminInfo admin,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<BrandModel> result = brandService.getAll(PageRequest.of(page, size));
        return ApiResponse.success(result.map(BrandAdminV1Dto.BrandResponse::from));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> getBrand(
        @AdminUser AdminInfo admin,
        @PathVariable Long brandId
    ) {
        BrandModel brand = brandService.getBrandForAdmin(brandId);
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(brand));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandAdminV1Dto.BrandResponse> update(
        @AdminUser AdminInfo admin,
        @PathVariable Long brandId,
        @RequestBody BrandAdminV1Dto.UpdateRequest request
    ) {
        BrandModel brand = brandService.update(brandId, request.name(), request.description());
        return ApiResponse.success(BrandAdminV1Dto.BrandResponse.from(brand));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Object> delete(@AdminUser AdminInfo admin, @PathVariable Long brandId) {
        brandFacade.delete(brandId);
        return ApiResponse.success();
    }
}
