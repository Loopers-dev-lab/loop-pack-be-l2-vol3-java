package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminController {

    private final BrandFacade brandFacade;

    @GetMapping
    public ApiResponse<List<BrandDto.BrandResponse>> getAllBrands() {
        List<BrandDto.BrandResponse> responses = brandFacade.getAllBrands().stream()
            .map(BrandDto.BrandResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> getBrand(@PathVariable Long brandId) {
        Brand brand = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BrandDto.BrandResponse> createBrand(@Valid @RequestBody BrandDto.CreateRequest request) {
        Brand brand = brandFacade.createBrand(request.name(), request.description());
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @PutMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandDto.UpdateRequest request
    ) {
        Brand brand = brandFacade.updateBrand(brandId, request.name(), request.description());
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }

    @DeleteMapping("/{brandId}")
    public ApiResponse<Object> deleteBrand(@PathVariable Long brandId) {
        brandFacade.deleteBrand(brandId);
        return ApiResponse.success();
    }
}
