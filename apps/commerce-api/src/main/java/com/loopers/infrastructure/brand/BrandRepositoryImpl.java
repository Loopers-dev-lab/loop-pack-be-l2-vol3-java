package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

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
    public Optional<Brand> findById(UUID id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(BrandEntity::toDomain);
    }

    @Override
    public Page<Brand> findAll(Pageable pageable) {
        return brandJpaRepository.findAllByDeletedAtIsNull(pageable)
                .map(BrandEntity::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return brandJpaRepository.existsById(id);
    }

    @Override
    public boolean existsByName(BrandName name) {
        return brandJpaRepository.existsByName(name.value());
    }

    @Override
    public void delete(Brand brand) {
        brandJpaRepository.findById(brand.id())
                .ifPresent(BrandEntity::delete);
    }
}
