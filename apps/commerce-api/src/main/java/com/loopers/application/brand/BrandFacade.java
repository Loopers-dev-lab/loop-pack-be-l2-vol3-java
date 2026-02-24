package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandService brandService;

    public BrandInfo register(String name, String description) {
        Brand brand = brandService.register(name, description);
        return BrandInfo.from(brand);
    }

    @Transactional
    public BrandInfo update(Long brandId, String name, String description) {
        Brand brand = brandService.getActiveBrand(brandId);
        brandService.update(brand, name, description);
        return BrandInfo.from(brand);
    }
}
