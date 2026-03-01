package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BrandRepositoryAdapter implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> findById(Long brandId) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(brandId);
    }

    @Override
    public Optional<Brand> findByIdIncludingDeleted(Long brandId) {
        return brandJpaRepository.findById(brandId);
    }

    @Override
    public Page<Brand> findAll(Pageable pageable) {
        return brandJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public List<Brand> findAllByIds(Collection<Long> brandIds) {
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(brandIds);
    }

    @Override
    public boolean existsByName(String name) {
        return brandJpaRepository.existsByNameAndDeletedAtIsNull(name);
    }
}
