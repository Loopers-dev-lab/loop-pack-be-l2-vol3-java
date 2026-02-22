package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandFacade;
import com.loopers.application.brand.BrandInfo;
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
    private final BrandFacade brandFacade;

    public BrandController(BrandService brandService, BrandFacade brandFacade) {
        this.brandService = brandService;
        this.brandFacade = brandFacade;
    }

    /** 활성 브랜드 목록 조회 (기존 BrandService 직접 사용) */
    @GetMapping
    @Override
    public ApiResponse<List<BrandResponse.BrandSummary>> getBrands() {
        List<BrandResponse.BrandSummary> brands = this.brandService.getAllActiveBrands().stream()
                .map(BrandInfo::from)
                .map(BrandResponse.BrandSummary::from)
                .toList();
        return ApiResponse.success(brands);
    }

    /** 브랜드 상세 조회 (BrandFacade → 브랜드 + ACTIVE 상품 목록) */
    @GetMapping("/{brandId}")
    @Override
    public ApiResponse<BrandResponse.BrandDetailWithProducts> getBrand(@PathVariable Long brandId) {
        BrandFacade.BrandDetailResult result = brandFacade.getBrandDetail(brandId);
        return ApiResponse.success(
                BrandResponse.BrandDetailWithProducts.from(result.brand(), result.products()));
    }
}
