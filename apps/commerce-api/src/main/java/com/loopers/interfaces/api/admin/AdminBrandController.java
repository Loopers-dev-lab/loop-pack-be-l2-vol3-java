package com.loopers.interfaces.api.admin;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.domain.brand.Brand;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.BrandDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/brands")
public class AdminBrandController {

    private final BrandApplicationService brandApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandDto.BrandResponse> createBrand(
            @Valid @RequestBody BrandDto.CreateBrandRequest request
    ) {
        Brand brand = brandApplicationService.create(request.toCommand());
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> getBrand(@PathVariable Long brandId) {
        Brand brand = brandApplicationService.findById(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> updateBrand(
            @PathVariable Long brandId,
            @Valid @RequestBody BrandDto.UpdateBrandRequest request
    ) {
        Brand brand = brandApplicationService.update(brandId, request.toCommand());
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Void> deleteBrand(@PathVariable Long brandId) {
        brandApplicationService.delete(brandId);
        return ApiResponse.success();
    }
}
