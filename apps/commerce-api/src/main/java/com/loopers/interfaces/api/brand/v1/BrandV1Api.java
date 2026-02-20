package com.loopers.interfaces.api.brand.v1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.brand.BrandResult;
import com.loopers.application.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/brands")
public class BrandV1Api implements BrandV1ApiSpec {

    private final BrandService brandService;

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandDto.BrandResponse> getActiveBrand(@PathVariable Long brandId) {
        BrandResult result = brandService.getActiveBrand(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(result));
    }
}
