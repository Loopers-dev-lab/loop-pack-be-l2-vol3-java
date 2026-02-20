package com.loopers.interfaces.api.brand.v1;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.brand.BrandResult;
import com.loopers.application.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.page.Page;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/brands")
public class BrandV1AdminApi implements BrandV1AdminApiSpec {

    private final BrandService brandService;

    @PostMapping
    @ResponseStatus(code = HttpStatus.CREATED)
    @Override
    public ApiResponse<BrandDto.CreateBrandResponse> createBrand(@RequestBody @Valid BrandDto.CreateBrandRequest request) {
        BrandResult result = brandService.createBrand(request.name(), request.logoUrl(), request.description());
        return ApiResponse.success(BrandDto.CreateBrandResponse.from(result));
    }

    @GetMapping
    @Override
    public ApiResponse<PageResponse<BrandDto.BrandResponse>> getBrands(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<BrandResult> result = brandService.getBrands(new PageSize(page, size));
        return ApiResponse.success(new PageResponse<>(BrandDto.BrandResponse.from(result.content()), result.hasNext()));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandDto.BrandResponse> getBrand(@PathVariable Long brandId) {
        BrandResult result = brandService.getBrand(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(result));
    }
}
