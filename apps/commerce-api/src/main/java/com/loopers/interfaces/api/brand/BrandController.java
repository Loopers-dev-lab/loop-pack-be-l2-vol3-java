package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandAppService;
import com.loopers.domain.brand.Brand;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {
    private final BrandAppService brandAppService;

    @GetMapping
    public ApiResponse<BrandDto.ListResponse> getAll() {
        List<Brand> brands = brandAppService.getAll();
        return ApiResponse.success(BrandDto.ListResponse.from(brands));
    }

    @GetMapping("/{id}")
    public ApiResponse<BrandDto.Response> getById(@PathVariable Long id) {
        Brand brand = brandAppService.getById(id);
        return ApiResponse.success(BrandDto.Response.from(brand));
    }
}
