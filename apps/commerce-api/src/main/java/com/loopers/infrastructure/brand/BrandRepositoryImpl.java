package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BrandRepositoryImpl implements BrandRepository {
    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        if (brand.getId() == null) {
            BrandJpaEntity entity = BrandJpaEntity.from(brand);
            BrandJpaEntity saved = brandJpaRepository.save(entity);
            return saved.toDomain();
        }

        BrandJpaEntity entity = brandJpaRepository.findById(brand.getId())
                .orElseThrow(() -> new IllegalStateException("Brand not found: " + brand.getId()));
        entity.update(brand);
        return entity.toDomain();
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findById(id)
                .map(BrandJpaEntity::toDomain);
    }

    @Override
    public List<Brand> findAll() {
        return brandJpaRepository.findAll().stream()
                .map(BrandJpaEntity::toDomain)
                .toList();
    }
}
