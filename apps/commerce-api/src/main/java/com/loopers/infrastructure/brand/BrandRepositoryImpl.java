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
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    // Command

    @Override
    public Brand save(Brand brand) {
        return brandJpaRepository.save(brand);
    }

    // Query

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return brandJpaRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return brandJpaRepository.existsByNameAndIdNot(name, id);
    }

    @Override
    public Page<Brand> findAll(String name, Boolean deleted, Pageable pageable) {
        return brandJpaRepository.findAll(name, deleted, pageable);
    }

    @Override
    public List<Brand> findAllByIdIn(Collection<Long> ids) {
        return brandJpaRepository.findAllByIdIn(ids);
    }

    @Override
    public Page<Brand> findAllActive(String name, Pageable pageable) {
        return brandJpaRepository.findAllActive(name, pageable);
    }
}
