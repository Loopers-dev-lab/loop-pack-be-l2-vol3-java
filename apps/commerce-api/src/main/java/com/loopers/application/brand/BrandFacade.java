package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandService brandService;

    // Command

    @Transactional
    public BrandInfo register(BrandCommand.Register command) {
        Brand brand = brandService.register(command);
        return BrandInfo.from(brand);
    }

    @Transactional
    public BrandInfo updateInfo(Long brandId, BrandCommand.UpdateInfo command) {
        Brand brand = brandService.updateInfo(brandId, command);
        return BrandInfo.from(brand);
    }

    @Transactional
    public void delete(Long brandId) {
        brandService.delete(brandId);
    }

    // Query

    @Transactional(readOnly = true)
    public Page<BrandInfo> getList(String name, Boolean deleted, Pageable pageable) {
        Page<Brand> brands = brandService.findBrands(name, deleted, pageable);
        return brands.map(BrandInfo::from);
    }

    @Transactional(readOnly = true)
    public BrandInfo getDetail(Long brandId) {
        Brand brand = brandService.getBrand(brandId);
        return BrandInfo.from(brand);
    }

    @Transactional(readOnly = true)
    public Page<BrandInfo> getActiveList(String name, Pageable pageable) {
        Page<Brand> brands = brandService.findActiveBrands(name, pageable);
        return brands.map(BrandInfo::from);
    }

    @Transactional(readOnly = true)
    public BrandInfo getActiveDetail(Long brandId) {
        Brand brand = brandService.getActiveBrand(brandId);
        return BrandInfo.from(brand);
    }
}
