package com.loopers.domain.brand;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional
    public Brand register(BrandCommand command) {
        String trimmedName = command.name() != null ? command.name().trim() : null;

        if (trimmedName != null && brandRepository.existsActiveByNameIgnoreCase(trimmedName)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }

        Brand brand = Brand.create(trimmedName, command.description(), command.logoUrl());
        return brandRepository.save(brand);
    }

    public Brand getBrand(Long brandId) {
        return brandRepository.findActiveById(brandId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."));
    }

    public Page<Brand> getBrands(Pageable pageable) {
        return brandRepository.findAllActive(pageable);
    }

    public List<Brand> getByIds(List<Long> ids) {
        return brandRepository.findAllActiveByIdIn(ids);
    }

    @Transactional
    public Brand updateBrand(Long brandId, BrandCommand command) {
        Brand brand = getBrand(brandId);

        String trimmedName = command.name() != null ? command.name().trim() : null;

        if (trimmedName != null && brandRepository.existsActiveByNameIgnoreCaseAndIdNot(trimmedName, brandId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 브랜드명입니다.");
        }

        brand.update(trimmedName, command.description(), command.logoUrl());
        return brand;
    }

    @Transactional
    public void deleteBrand(Long brandId) {
        Brand brand = getBrand(brandId);
        brand.delete();
    }

}
