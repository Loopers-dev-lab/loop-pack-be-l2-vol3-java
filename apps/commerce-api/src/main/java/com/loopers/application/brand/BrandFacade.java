package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandService brandService;

    @Transactional
    public BrandInfo createBrand(CreateBrandCommand command) {
        Brand brand = brandService.create(command.name(), command.description());
        return BrandInfo.from(brand);
    }

    @Transactional
    public BrandInfo updateBrand(UpdateBrandCommand command) {
        Brand brand = brandService.update(command.brandId(), command.name(), command.description());
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public BrandInfo getBrand(Long brandId) {
        Brand brand = brandService.getById(brandId);
        return BrandInfo.from(brand);
    }
}
