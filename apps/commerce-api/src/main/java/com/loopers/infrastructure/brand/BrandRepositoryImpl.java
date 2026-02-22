package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 브랜드 리포지토리 구현체 (Infrastructure Layer)
 *
 * Domain 계층의 BrandRepository 포트를 구현한다.
 * Spring Data JPA의 기술 타입(PageRequest 등)을 여기서 변환하여
 * Domain 계층이 인프라에 의존하지 않도록 한다.
 */
@Repository
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    public BrandRepositoryImpl(BrandJpaRepository brandJpaRepository) {
        this.brandJpaRepository = brandJpaRepository;
    }

    @Override
    public Brand save(Brand brand) {
        return this.brandJpaRepository.save(brand);
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return this.brandJpaRepository.findById(id);
    }

    @Override
    public List<Brand> findAll(int page, int size) {
        // Spring의 PageRequest를 Infrastructure에서만 사용 (DIP 준수)
        return this.brandJpaRepository.findAll(PageRequest.of(page, size)).getContent();
    }

    @Override
    public long count() {
        return this.brandJpaRepository.count();
    }

    @Override
    public List<Brand> findAllByIdIn(List<Long> ids) {
        return this.brandJpaRepository.findAllById(ids);
    }

    @Override
    public List<Brand> findAllActive() {
        return this.brandJpaRepository.findAllByStatusAndDeletedAtIsNull(BrandStatus.ACTIVE);
    }
}
