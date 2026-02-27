package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.dto.FindBrandApiResDto;
import com.loopers.interfaces.api.brand.dto.FindBrandListApiResDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/brands")
public class BrandV1Controller implements BrandV1ApiSpec {

    private final BrandFacade brandFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<FindBrandListApiResDto>> findBrandList(Pageable pageable) {
        return ApiResponse.success(brandFacade.findBrandList(pageable).map(FindBrandListApiResDto::from));
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<FindBrandApiResDto> findBrand(@PathVariable Long brandId) {
        return ApiResponse.success(FindBrandApiResDto.from(brandFacade.findBrand(brandId)));
    }

}
