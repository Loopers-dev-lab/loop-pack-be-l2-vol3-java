package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandResult;
import com.loopers.application.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/brands")
public class BrandAdminV1Controller implements BrandAdminV1ApiSpec {

    private final BrandService brandService;

    @GetMapping
    @Override
    public ApiResponse<List<BrandV1Dto.BrandResponse>> getAllBrands() {
        List<BrandResult> results = brandService.findAll();
        List<BrandV1Dto.BrandResponse> responses = results.stream()
            .map(BrandV1Dto.BrandResponse::from)
            .toList();
        return ApiResponse.success(responses);
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandResult result = brandService.findById(brandId);
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(result));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> createBrand(@Valid @RequestBody BrandV1Dto.BrandCreateRequest request) {
        BrandResult result = brandService.create(request.toInfo());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(result));
    }

    @PutMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Dto.BrandResponse> updateBrand(
        @PathVariable Long brandId,
        @Valid @RequestBody BrandV1Dto.BrandUpdateRequest request
    ) {
        BrandResult result = brandService.update(brandId, request.toInfo());
        return ApiResponse.success(BrandV1Dto.BrandResponse.from(result));
    }

    @DeleteMapping("/{brandId}")
    @Override
    public ApiResponse<Object> deleteBrand(@PathVariable Long brandId) {
        brandService.delete(brandId);
        return ApiResponse.success();
    }
}
