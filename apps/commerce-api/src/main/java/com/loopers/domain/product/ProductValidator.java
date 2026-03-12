package com.loopers.domain.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductValidator {

    private final BrandRepository brandRepository;

    public void validateBrandExists(Long brandId) {
        Brand brand = brandRepository.findById(brandId)
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));

        if (brand.getDeletedAt() != null) {
            throw new CoreException(ErrorType.BRAND_DELETED);
        }
    }
}
