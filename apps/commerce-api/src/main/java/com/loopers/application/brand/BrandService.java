package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.brand.BrandInfo;
import com.loopers.domain.product.ProductDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandDomainService brandDomainService;
    private final ProductDomainService productDomainService;

    @Transactional(readOnly = true)
    public BrandResult findById(Long id) {
        Brand brand = brandDomainService.findById(id);
        return BrandResult.from(brand);
    }

    @Transactional(readOnly = true)
    public List<BrandResult> findAll() {
        return brandDomainService.findAll().stream()
            .map(BrandResult::from)
            .toList();
    }

    @Transactional
    public BrandResult create(BrandInfo info) {
        Brand brand = brandDomainService.create(info);
        return BrandResult.from(brand);
    }

    @Transactional
    public BrandResult update(Long id, BrandInfo info) {
        Brand brand = brandDomainService.update(id, info);
        return BrandResult.from(brand);
    }

    @Transactional
    public void delete(Long brandId) {
        // 해당 브랜드의 모든 상품 soft delete (다른 BC)
        productDomainService.deleteAllByBrandId(brandId);
        // Brand soft delete
        brandDomainService.delete(brandId);
    }
}
