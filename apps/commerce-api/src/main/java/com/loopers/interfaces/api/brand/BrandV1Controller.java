package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.BrandDto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class BrandV1Controller {

    private final BrandApplicationService brandApplicationService;

    @GetMapping("/api/v1/brands/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandDto.BrandInfo brand = brandApplicationService.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand));
    }

    @GetMapping("/api-admin/v1/brands")
    public ApiResponse<BrandV1Dto.BrandPageResponse> getAdminBrands(Pageable pageable) {
        return ApiResponse.success(BrandV1Dto.BrandPageResponse.from(brandApplicationService.getAdminBrands(pageable)));
    }

    @GetMapping("/api-admin/v1/brands/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> getAdminBrand(@PathVariable Long brandId) {
        BrandDto.BrandInfo brand = brandApplicationService.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand));
    }

    @PostMapping("/api-admin/v1/brands")
    public ApiResponse<BrandV1Dto.BrandResponse> createBrand(@Valid @RequestBody BrandV1Dto.CreateRequest request) {
        try {
            BrandDto.BrandInfo brand = brandApplicationService.register(request.name(), request.description());
            return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/api-admin/v1/brands/{brandId}")
    public ApiResponse<BrandV1Dto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandV1Dto.UpdateRequest request
    ) {
        try {
            BrandDto.BrandInfo brand = brandApplicationService.update(brandId, request.name(), request.description());
            return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand));
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/api-admin/v1/brands/{brandId}")
    public ApiResponse<Void> deleteBrand(@PathVariable Long brandId) {
        brandApplicationService.delete(brandId);
        return ApiResponse.success(null);
    }

    @PostMapping("/api-admin/v1/brands/{brandId}/restore")
    public ApiResponse<BrandV1Dto.BrandResponse> restoreBrand(@PathVariable Long brandId) {
        BrandDto.BrandInfo brand = brandApplicationService.restore(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(brand));
    }
}
