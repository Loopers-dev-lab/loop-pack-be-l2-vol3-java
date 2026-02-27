package com.loopers.infrastructure.brand.repository.impl;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.repository.BrandRepository;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        BrandEntity entity = brandJpaRepository.save(BrandEntity.toEntity(brand));
        return entity.toModel();
    }

    @Override
    public void update(Brand brand) {
        BrandEntity entity = brandJpaRepository.findById(brand.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));
        entity.update(brand.getName().value(), brand.getDescription());
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findById(id).map(BrandEntity::toModel);
    }

    @Override
    public Page<Brand> findAll(Pageable pageable) {
        return brandJpaRepository.findAll(pageable).map(BrandEntity::toModel);
    }

    @Override
    public void deleteById(Long id) {
        brandJpaRepository.deleteById(id);
    }
}
