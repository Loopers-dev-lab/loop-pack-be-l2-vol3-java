package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    public Optional<Brand> findById(Long id){
        return brandJpaRepository.findById(id);
    }

    @Override
    public List<Brand> findAllByIds(List<Long> ids) {
        return brandJpaRepository.findAllById(ids);
    }

    @Override
    public Page<Brand> findAll(Pageable pageable) {
        return brandJpaRepository.findAll(pageable);
    }

    @Override
    public boolean existsByName(String name) {
        return brandJpaRepository.existsByNameAndDeletedAtIsNull(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return brandJpaRepository.existsByNameAndIdNotAndDeletedAtIsNull(name, id);
    }

    public Brand save(Brand brand){
        return brandJpaRepository.save(brand);
    }

}
