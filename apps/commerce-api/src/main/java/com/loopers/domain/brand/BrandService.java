package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
public Brand register(String name, String description) {
        if (brandRepository.existsByName(name)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 등록된 브랜드입니다");
        }

        Brand brand = Brand.create(name, description);
        return brandRepository.save(brand);
    }

    public Brand getActiveBrand(Long brandId) {
        return brandRepository.findById(brandId)
                .filter(brand -> !brand.isDeleted())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다"));
    }

    @Transactional
    public void delete(Brand brand) {
        brand.delete();
    }

    public void update(Brand brand, String name, String description) {
        if (brandRepository.existsByNameAndIdNot(name, brand.getId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 등록된 브랜드입니다");
        }

        brand.update(name, description);
    }
}
