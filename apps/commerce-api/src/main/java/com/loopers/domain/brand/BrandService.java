package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;

    // Command

    @Transactional
    public Brand register(String name, String description) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 등록된 브랜드입니다");
        }

        Brand brand = Brand.create(name, description);
        return brandRepository.save(brand);
    }

    @Transactional
    public void update(Brand brand, String name, String description) {
        if (brandRepository.existsByNameAndIdNot(name, brand.getId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 등록된 브랜드입니다");
        }

        brand.update(name, description);
    }

    @Transactional
    public void delete(Brand brand) {
        brand.delete();
    }

    // Query

    public Brand getBrand(Long brandId) {
        return brandRepository.findById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다"));
    }

    public Page<Brand> findBrands(String name, Boolean deleted, Pageable pageable) {
        return brandRepository.findAll(name, deleted, pageable);
    }
}
