package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand create(Brand brand) {
        BrandEntity brandEntity = BrandEntity.create(brand);

        return BrandEntity.toDomain(brandJpaRepository.save(brandEntity));
    }

    @Override
    public Brand update(Brand brand) {
        BrandEntity brandEntity = brandJpaRepository.findById(brand.getId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Brand.BRAND_NOT_FOUND));
        brandEntity.update(brand);

        return BrandEntity.toDomain(brandJpaRepository.save(brandEntity));
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findById(id)
            .map(BrandEntity::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return brandJpaRepository.existsById(id);
    }
}
