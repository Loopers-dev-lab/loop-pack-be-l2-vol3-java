package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        BrandEntity entity = BrandEntity.from(brand);
        BrandEntity saved = brandJpaRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findById(id)
                .filter(entity -> entity.getDeletedAt() == null)
                .map(BrandEntity::toDomain);
    }

    @Override
    public boolean existsById(Long id) {
        return brandJpaRepository.existsById(id);
    }

    @Override
    public boolean existsByName(BrandName name) {
        return brandJpaRepository.existsByName(name.value());
    }

    @Override
    public void deleteRelatedProducts(Long brandId) {
        brandJpaRepository.softDeleteProductsByBrandId(brandId);
    }

    @Override
    public void delete(Brand brand) {
        brandJpaRepository.findById(brand.id())
                .ifPresent(BrandEntity::delete);
    }
}
