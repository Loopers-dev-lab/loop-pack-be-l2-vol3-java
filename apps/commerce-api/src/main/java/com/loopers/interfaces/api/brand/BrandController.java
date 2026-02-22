package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.interfaces.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/brands")
public class BrandController implements BrandApiSpec {

    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @GetMapping
    @Override
    public ApiResponse<List<BrandResponse.BrandSummary>> getBrands() {
        List<BrandResponse.BrandSummary> brands = this.brandService.getAllActiveBrands().stream()
                .map(BrandInfo::from)
                .map(BrandResponse.BrandSummary::from)
                .toList();
        return ApiResponse.success(brands);
    }

    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandResponse.BrandDetail> getBrand(@PathVariable Long brandId) {
        Brand brand = this.brandService.getActiveBrand(brandId);
        BrandInfo info = BrandInfo.from(brand);
        return ApiResponse.success(BrandResponse.BrandDetail.from(info));
    }
}
