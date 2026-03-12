package com.loopers.interfaces.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.dto.BrandResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.brand.dto.AdminBrandV1Dto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class AdminBrandV1Controller implements AdminBrandV1ApiSpec {

    private final BrandFacade brandFacade;

    @PostMapping
    @Override
    public ApiResponse<Object> register(
        @Valid @RequestBody AdminBrandV1Dto.RegisterRequest request
    ) {
        brandFacade.registerBrand(request.toCriteria());
        return ApiResponse.success();
    }

    @GetMapping
    @Override
    public ApiResponse<AdminBrandV1Dto.ListResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<BrandResult> brandInfoPage = brandFacade.getBrands(PageRequest.of(page, size));
        return ApiResponse.success(
                new AdminBrandV1Dto.ListResponse(
                        brandInfoPage.getNumber(),
                        brandInfoPage.getSize(),
                        brandInfoPage.getTotalElements(),
                        brandInfoPage.getTotalPages(),
                        brandInfoPage.getContent().stream()
                                .map(AdminBrandV1Dto.ListResponse.ListItem::from)
                                .toList()));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<AdminBrandV1Dto.DetailResponse> getById(
        @PathVariable Long brandId
    ) {
        BrandResult brandInfo = brandFacade.getBrand(brandId);
        return ApiResponse.success(AdminBrandV1Dto.DetailResponse.from(brandInfo));
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<Object> update(
        @PathVariable Long brandId,
        @Valid @RequestBody AdminBrandV1Dto.UpdateRequest request
    ) {
        brandFacade.updateBrand(brandId, request.toCriteria());
        return ApiResponse.success();
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Object> delete(
        @PathVariable Long brandId
    ) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success();
    }
}
