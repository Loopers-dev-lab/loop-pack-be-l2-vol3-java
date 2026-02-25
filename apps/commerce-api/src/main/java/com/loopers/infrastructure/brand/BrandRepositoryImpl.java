package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 브랜드 리포지토리 구현체 (Infrastructure Layer)
 *
 * Domain 계층의 BrandRepository 포트를 구현한다.
 * Mapper를 활용하여 Domain ↔ Entity 변환한다.
 * Spring Data JPA의 기술 타입(PageRequest 등)을 여기서 변환하여
 * Domain 계층이 인프라에 의존하지 않도록 한다.
 */
@Repository
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;
    private final BrandMapper brandMapper;

    public BrandRepositoryImpl(BrandJpaRepository brandJpaRepository, BrandMapper brandMapper) {
        this.brandJpaRepository = brandJpaRepository;
        this.brandMapper = brandMapper;
    }

    @Override
    public Brand save(Brand brand) {
        // Domain → Entity
        BrandEntity entity = brandMapper.toEntity(brand);

        // JPA save
        BrandEntity saved = brandJpaRepository.save(entity);

        // Entity → Domain
        return brandMapper.toDomain(saved);
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findById(id)
            .map(brandMapper::toDomain);  // Entity → Domain
    }

    @Override
    public List<Brand> findAll(int page, int size) {
        // Spring의 PageRequest를 Infrastructure에서만 사용 (DIP 준수)
        return brandJpaRepository.findAll(PageRequest.of(page, size))
            .getContent()
            .stream()
            .map(brandMapper::toDomain)  // Entity → Domain
            .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return brandJpaRepository.count();
    }

    @Override
    public List<Brand> findAllByIdIn(List<Long> ids) {
        return brandJpaRepository.findAllByIdInAndDeletedAtIsNull(ids).stream()
            .map(brandMapper::toDomain)  // Entity → Domain
            .collect(Collectors.toList());
    }

    @Override
    public List<Brand> findAllActive() {
        return brandJpaRepository.findAllByStatusAndDeletedAtIsNull(BrandStatus.ACTIVE.name()).stream()
            .map(brandMapper::toDomain)  // Entity → Domain
            .collect(Collectors.toList());
    }
}
