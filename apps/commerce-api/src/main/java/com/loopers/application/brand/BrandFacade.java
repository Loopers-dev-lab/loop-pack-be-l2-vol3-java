package com.loopers.application.brand;

import com.loopers.application.brand.dto.BrandCriteria;
import com.loopers.application.brand.dto.BrandResult;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.support.cache.CacheType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrandFacade {

    private final BrandService brandService;
    private final ProductService productService;

    @CacheEvict(cacheNames = CacheType.Names.BRAND_LIST, allEntries = true)
    @Transactional
    public void registerBrand(BrandCriteria.Register criteria) {
        brandService.register(criteria.name());
    }

    @Transactional(readOnly = true)
    public BrandResult getBrand(Long id) {
        BrandModel brandModel = brandService.getById(id);
        return BrandResult.from(brandModel);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheType.Names.BRAND_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_LATEST, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_PRICE, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_LIKES, allEntries = true)})
    @Transactional
    public void updateBrand(Long id, BrandCriteria.Update criteria) {
        brandService.update(id, criteria.name());
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheType.Names.BRAND_LIST, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_LATEST, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_PRICE, allEntries = true),
            @CacheEvict(cacheNames = CacheType.Names.PRODUCT_LIST_LIKES, allEntries = true)})
    @Transactional
    public void deleteBrand(Long id) {
        brandService.delete(id);
        productService.deleteAllByBrandId(id);
    }

    @Cacheable(cacheNames = CacheType.Names.BRAND_LIST, key = "'all'")
    @Transactional(readOnly = true)
    public BrandResult.ListPage getBrands(Pageable pageable) {
        return BrandResult.ListPage.from(
                brandService.getAll(pageable).map(BrandResult::from));
    }
}
