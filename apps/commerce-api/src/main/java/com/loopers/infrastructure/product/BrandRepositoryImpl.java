package com.loopers.infrastructure.product;

import com.loopers.domain.product.Brand;
import com.loopers.domain.product.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
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
}
