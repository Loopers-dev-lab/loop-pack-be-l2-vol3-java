package com.loopers.domain.brand.service;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandService {
    private final BrandRepository brandRepository;

    public Brand createBrand(BrandCommand.Create command) {
        Brand brand = Brand.create(command);
        return brandRepository.save(brand);
    }

    public Brand updateBrand(Long brandId, BrandCommand.Update command) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
        brand.update(command);
        brandRepository.update(brand);
        return brand;
    }

    public void deleteBrand(Long brandId) {
        brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
        brandRepository.deleteById(brandId);
    }

    public Brand findBrand(Long brandId) {
        return brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
    }

    public Page<Brand> findBrandList(Pageable pageable) {
        return brandRepository.findAll(pageable);
    }
}
