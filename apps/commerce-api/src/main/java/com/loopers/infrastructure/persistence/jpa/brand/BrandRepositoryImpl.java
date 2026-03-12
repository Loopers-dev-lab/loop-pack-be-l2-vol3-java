package com.loopers.infrastructure.persistence.jpa.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * BrandRepository 구현체.
 * JPA를 사용하여 Brand 도메인 객체를 영속화.
 * Domain ↔ JPA Entity 변환은 BrandMapper를 통해 수행.
 */
@Repository
@RequiredArgsConstructor
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository jpaRepository;

    @Override
    public Brand save(Brand brand) {
        BrandJpaEntity entity;

        if (brand.getId() == null) {
            // 신규 생성
            entity = BrandMapper.toJpaEntity(brand);
        } else {
            // 기존 엔티티 업데이트
            entity = jpaRepository.findById(brand.getId())
                .orElseGet(() -> BrandMapper.toJpaEntity(brand));
            BrandMapper.updateJpaEntity(entity, brand);
        }

        BrandJpaEntity saved = jpaRepository.save(entity);
        return BrandMapper.toDomain(saved);
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return jpaRepository.findById(id)
            .map(BrandMapper::toDomain);
    }

    @Override
    public Optional<Brand> findByIdActive(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id)
            .map(BrandMapper::toDomain);
    }

    @Override
    public List<Brand> findAllActive() {
        return jpaRepository.findAllByDeletedAtIsNull().stream()
            .map(BrandMapper::toDomain)
            .toList();
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public boolean existsByNameAndIdNot(String name, Long id) {
        return jpaRepository.existsByNameAndIdNot(name, id);
    }
}