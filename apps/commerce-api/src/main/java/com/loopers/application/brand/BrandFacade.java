package com.loopers.application.brand;

import com.loopers.application.brand.dto.BrandCriteria;
import com.loopers.application.brand.dto.BrandResult;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    @Transactional
    public void registerBrand(BrandCriteria.Register criteria) {
        brandService.register(criteria.name());
    }

    @Transactional(readOnly = true)
    public BrandResult getBrand(Long id) {
        BrandModel brandModel = brandService.getById(id);
        return BrandResult.from(brandModel);
    }

    @Transactional
    public void updateBrand(Long id, BrandCriteria.Update criteria) {
        brandService.update(id, criteria.name());
    }

    @Transactional
    public void deleteBrand(Long id) {
        brandService.delete(id);
        productService.deleteAllByBrandId(id);
    }

    @Transactional(readOnly = true)
    public Page<BrandResult> getBrands(Pageable pageable) {
        return brandService.getAll(pageable).map(BrandResult::from);
    }
}
