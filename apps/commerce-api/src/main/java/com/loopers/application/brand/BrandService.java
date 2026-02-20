package com.loopers.application.brand;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public BrandResult createBrand(String name, String logoUrl, String description) {
        if (brandRepository.existsByNameAndDeletedAtIsNull(name)) {
            throw new CoreException(ErrorType.ALREADY_EXIST_BRAND_NAME);
        }
        Brand brand = Brand.create(name, logoUrl, description);
        Brand saved = brandRepository.save(brand);
        return BrandResult.from(saved);
    }
}
