package com.loopers.application.brand;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    @Transactional
    public BrandInfo register(String name) {
        Brand brand = brandService.register(name);
        return BrandInfo.from(brand);
    }

    public BrandInfo getById(Long id) {
        Brand brand = brandService.getById(id);
        return BrandInfo.from(brand);
    }

    public PageResult<BrandInfo> getAll(int page, int size) {
        PageResult<Brand> result = brandService.getAll(page, size);
        return result.map(BrandInfo::from);
    }

    @Transactional
    public BrandInfo update(Long id, String name) {
        Brand brand = brandService.update(id, name);
        return BrandInfo.from(brand);
    }

    @Transactional
    public void delete(Long id) {
        brandService.getById(id);
        productService.deleteAllByBrandId(id);
        brandService.delete(id);
    }
}
