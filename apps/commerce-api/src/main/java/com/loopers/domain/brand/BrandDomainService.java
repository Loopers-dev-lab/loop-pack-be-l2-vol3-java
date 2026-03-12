package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BrandDomainService {

    private final BrandRepository brandRepository;
    private final BrandValidator brandValidator;

    public Brand create(BrandInfo info) {
        brandValidator.validateNameNotDuplicated(info.name());
        Brand brand = Brand.create(info.name(), info.description(), info.logoUrl());
        return brandRepository.save(brand);
    }

    public Brand update(Long id, BrandInfo info) {
        Brand brand = findById(id);
        brandValidator.validateNameNotDuplicatedExcept(info.name(), id);
        brand.update(info.name(), info.description(), info.logoUrl());
        return brandRepository.save(brand);
    }

    public Brand findById(Long id) {
        return brandRepository.findByIdActive(id)
            .orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
    }

    public List<Brand> findAll() {
        return brandRepository.findAllActive();
    }

    public void delete(Long id) {
        Brand brand = findById(id);
        brand.delete();
        brandRepository.save(brand);
    }
}
