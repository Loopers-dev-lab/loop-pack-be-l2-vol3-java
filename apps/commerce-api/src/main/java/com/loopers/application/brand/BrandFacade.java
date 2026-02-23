package com.loopers.application.brand;

import com.loopers.application.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class BrandFacade {
    private final BrandService brandService;
    private final ProductService productService;

    public BrandInfo register(String name, String description) {
        return brandService.register(name, description);
    }

    public BrandInfo getBrand(Long id) {
        return brandService.getBrand(id);
    }

    public Page<BrandInfo> getBrands(Pageable pageable) {
        return brandService.getBrands(pageable);
    }

    public BrandInfo update(Long id, String name, String description) {
        return brandService.update(id, name, description);
    }

    @Transactional
    public void delete(Long brandId) {
        productService.deleteAllByBrandId(brandId);
        brandService.delete(brandId);
    }
}
