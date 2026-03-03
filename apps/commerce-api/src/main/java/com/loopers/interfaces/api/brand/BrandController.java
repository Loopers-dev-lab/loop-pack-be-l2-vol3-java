package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.domain.brand.Brand;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/brands")
public class BrandController {

    private final BrandApplicationService brandApplicationService;

    @GetMapping("/{brandId}")
    public ApiResponse<BrandDto.BrandResponse> getBrand(@PathVariable UUID brandId) {
        Brand brand = brandApplicationService.findById(brandId);
        return ApiResponse.success(BrandDto.BrandResponse.from(brand));
    }
}
