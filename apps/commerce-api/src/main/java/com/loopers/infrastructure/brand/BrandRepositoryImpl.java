package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BrandRepositoryImpl implements BrandRepository {
    private final BrandJpaRepository brandJpaRepository;

    @Override
    public BrandModel save(BrandModel brandModel) {
        return brandJpaRepository.save(brandModel);
    }

    @Override
    public Optional<BrandModel> findById(Long id) {
        return brandJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<BrandModel> findByName(String name) {
        return brandJpaRepository.findByNameAndDeletedAtIsNull(name);
    }

    @Override
    public Page<BrandModel> findAll(Pageable pageable) {
        return brandJpaRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Override
    public List<BrandModel> findAllByIdIn(List<Long> ids) {
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }
}
