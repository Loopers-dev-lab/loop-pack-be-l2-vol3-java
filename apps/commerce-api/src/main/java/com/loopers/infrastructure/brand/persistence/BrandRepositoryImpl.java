package com.loopers.infrastructure.brand.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> findById(Long brandId) {
        return brandJpaRepository.findById(brandId);
    }

    @Override
    public List<Brand> findAllByIdInAndDeletedAtIsNull(List<Long> brandIds) {
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(brandIds);
    }

    @Override
    public Optional<Brand> findByIdAndDeletedAtIsNull(Long brandId) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public Slice<Brand> findAllBy(Pageable pageable) {
        return brandJpaRepository.findAllBy(pageable);
    }

    @Override
    public boolean existsById(Long brandId) {
        return brandJpaRepository.existsById(brandId);
    }

    @Override
    public boolean existsByIdAndDeletedAtIsNull(Long brandId) {
        return brandJpaRepository.existsByIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public boolean existsByNameAndDeletedAtIsNull(String name) {
        return brandJpaRepository.existsByName_ValueAndDeletedAtIsNull(name);
    }

    @Override
    public boolean existsByIdNotAndNameAndDeletedAtIsNull(Long brandId, String name) {
        return brandJpaRepository.existsByIdNotAndName_ValueAndDeletedAtIsNull(brandId, name);
    }
}
