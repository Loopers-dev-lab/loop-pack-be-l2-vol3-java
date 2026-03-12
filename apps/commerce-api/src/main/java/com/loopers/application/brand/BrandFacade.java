package com.loopers.application.brand;

import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    public BrandInfo register(String name, String description) {
        return BrandInfo.from(brandService.register(name, description));
    }

    public BrandInfo getBrand(Long brandId) {
        return BrandInfo.from(brandService.getBrand(brandId));
    }

    public BrandInfo getBrandForAdmin(Long brandId) {
        return BrandInfo.from(brandService.getBrandForAdmin(brandId));
    }

    public BrandInfo update(Long brandId, String name, String description) {
        return BrandInfo.from(brandService.update(brandId, name, description));
    }

    @Transactional
    public void delete(Long brandId) {
        brandService.delete(brandId);
        productService.deleteAllByBrandId(brandId);
    }

    public Page<BrandInfo> getAll(Pageable pageable) {
        return brandService.getAll(pageable).map(BrandInfo::from);
    }
}
