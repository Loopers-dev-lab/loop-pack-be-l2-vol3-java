package com.loopers.interfaces.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.dto.BrandResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.brand.dto.BrandV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller implements BrandV1ApiSpec {

    private final BrandFacade brandFacade;

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandV1Dto.DetailResponse> getById(
        @PathVariable Long brandId
    ) {
        BrandResult brandInfo = brandFacade.getBrand(brandId);
        return ApiResponse.success(BrandV1Dto.DetailResponse.from(brandInfo));
    }
}
