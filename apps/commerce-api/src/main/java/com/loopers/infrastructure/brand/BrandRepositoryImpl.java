package com.loopers.infrastructure.brand;

import com.loopers.domain.PageResult;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<Brand> findByIdWithLock(Long id) {
        return brandJpaRepository.findByIdWithLock(id);
    }

    @Override
    public List<Brand> findAllByIds(Set<Long> ids) {
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public PageResult<Brand> findAll(int page, int size) {
        Page<Brand> result = brandJpaRepository.findAllByDeletedAtIsNull(PageRequest.of(page, size));
        return new PageResult<>(
            result.getContent(),
            result.getNumber(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }
}
