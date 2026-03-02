package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    @Override
    public Brand save(Brand brand) {
        return brandJpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findById(id);
    }

    @Override
    public Optional<Brand> findActiveById(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public boolean existsActiveByNameIgnoreCase(String name) {
        return brandJpaRepository.existsByNameIgnoreCaseAndDeletedAtIsNull(name);
    }

    @Override
    public boolean existsActiveByNameIgnoreCaseAndIdNot(String name, Long id) {
        return brandJpaRepository.existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(name, id);
    }

    @Override
    public Page<Brand> findAllActive(Pageable pageable) {
        return brandJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public List<Brand> findAllActiveByIdIn(List<Long> ids) {
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }
}
