package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.domain.brand.Brand;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/brands")
public class BrandController {

    private final BrandFacade brandFacade;

    @GetMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> getBrand(@PathVariable Long brandId) {
        Brand brand = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }
}
