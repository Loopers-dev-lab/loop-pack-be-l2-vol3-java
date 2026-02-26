package com.loopers.infrastructure.brand;

import com.loopers.domain.model.brand.Brand;
import com.loopers.domain.model.brand.BrandData;
import com.loopers.domain.model.brand.BrandName;
import com.loopers.domain.repository.BrandRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandJpaRepository brandJpaRepository;

    public BrandRepositoryImpl(BrandJpaRepository brandJpaRepository) {
        this.brandJpaRepository = brandJpaRepository;
    }

    @Override
    public Brand save(Brand brand) {
        BrandJpaEntity entity = toEntity(brand);
        BrandJpaEntity saved = brandJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Brand> findById(Long id) {
        return brandJpaRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Optional<Brand> findActiveById(Long id) {
        return findById(id).filter(b -> !b.isDeleted());
    }

    @Override
    public List<Brand> findAllActive() {
        return brandJpaRepository.findAllByDeletedAtIsNull().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Brand> findAllByIds(List<Long> ids) {
        return brandJpaRepository.findAllByIdIn(ids).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByName(BrandName name) {
        return brandJpaRepository.existsByName(name.getValue());
    }

    private BrandJpaEntity toEntity(Brand brand) {
        return new BrandJpaEntity(
                brand.getId(),
                brand.getName().getValue(),
                brand.getDescription(),
                brand.getCreatedAt(),
                brand.getUpdatedAt(),
                brand.getDeletedAt()
        );
    }

    private Brand toDomain(BrandJpaEntity entity) {
        return Brand.reconstitute(new BrandData(
                entity.getId(),
                BrandName.of(entity.getName()),
                entity.getDescription(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        ));
    }
}
