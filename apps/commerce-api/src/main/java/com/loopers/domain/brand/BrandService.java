package com.loopers.domain.brand;

import com.loopers.support.error.BrandErrorType;
import com.loopers.support.error.CoreException;
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    public Brand create(String name, String description) {
        Brand brand = Brand.create(name, description);
        return brandRepository.save(brand);
    }

    public Brand getById(Long id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new CoreException(BrandErrorType.BRAND_NOT_FOUND));
        brand.validateNotDeleted();
        return brand;
    }

    public Brand getActiveBrand(Long id) {
        Brand brand = getById(id);
        if (!brand.isActive()) {
            throw new CoreException(BrandErrorType.INACTIVE_BRAND);
        }
        return brand;
    }

    public Brand update(Long id, String name, String description) {
        Brand brand = getById(id);
        brand.update(name, description);
        return brand;
    }

    public void delete(Long id) {
        Brand brand = getById(id);
        brand.delete();
    }
}
