package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    public BrandRepositoryImpl(BrandJpaRepository brandJpaRepository) {
        this.brandJpaRepository = brandJpaRepository;
    }

    @Override
    public Optional<BrandModel> findById(Long id) {
        return brandJpaRepository.findById(id);
    }

    @Override
    public Optional<BrandModel> findByIdAndNotDeleted(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public BrandModel save(BrandModel brand) {
        return brandJpaRepository.save(brand);
    }
}
