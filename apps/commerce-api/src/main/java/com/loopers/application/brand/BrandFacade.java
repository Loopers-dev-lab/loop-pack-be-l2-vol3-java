package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandFacade {

    private final BrandService brandService;

    // Command

    @Transactional
    public BrandInfo register(String name, String description) {
        Brand brand = brandService.register(name, description);
        return BrandInfo.from(brand);
    }

    @Transactional
    public BrandInfo update(Long brandId, String name, String description) {
        Brand brand = brandService.update(brandId, name, description);
        return BrandInfo.from(brand);
    }

    @Transactional
    public void delete(Long brandId) {
        brandService.delete(brandId);
    }

    // Query

    public Page<BrandInfo> getList(String name, Boolean deleted, Pageable pageable) {
        Page<Brand> brands = brandService.findBrands(name, deleted, pageable);
        return brands.map(BrandInfo::from);
    }

    public BrandInfo getDetail(Long brandId) {
        Brand brand = brandService.getBrand(brandId);
        return BrandInfo.from(brand);
    }

    public Page<BrandInfo> getActiveList(String name, Pageable pageable) {
        Page<Brand> brands = brandService.findActiveBrands(name, pageable);
        return brands.map(BrandInfo::from);
    }

    public BrandInfo getActiveDetail(Long brandId) {
        Brand brand = brandService.getActiveBrand(brandId);
        return BrandInfo.from(brand);
    }
}
