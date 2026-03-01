package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class AdminBrandV1Controller implements AdminBrandV1ApiSpec {

    private final BrandApplicationService brandApplicationService;

    @PostMapping
    @Override
    public ApiResponse<AdminBrandV1Dto.BrandResponse> create(@Valid @RequestBody AdminBrandV1Dto.CreateRequest request) {
        Brand brand = brandApplicationService.register(request.name());
        return ApiResponse.success(AdminBrandV1Dto.BrandResponse.from(brand));
    }

    @GetMapping
    @Override
    public ApiResponse<AdminBrandV1Dto.BrandPageResponse> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<Brand> result = brandApplicationService.getAll(page, size);
        return ApiResponse.success(AdminBrandV1Dto.BrandPageResponse.from(result));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<AdminBrandV1Dto.BrandResponse> getById(@PathVariable Long brandId) {
        Brand brand = brandApplicationService.getById(brandId);
        return ApiResponse.success(AdminBrandV1Dto.BrandResponse.from(brand));
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<AdminBrandV1Dto.BrandResponse> update(
        @PathVariable Long brandId,
        @Valid @RequestBody AdminBrandV1Dto.UpdateRequest request
    ) {
        Brand brand = brandApplicationService.update(brandId, request.name());
        return ApiResponse.success(AdminBrandV1Dto.BrandResponse.from(brand));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Void> delete(@PathVariable Long brandId) {
        brandApplicationService.delete(brandId);
        return ApiResponse.success();
    }
}
