package com.loopers.infrastructure.brand;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BrandJpaRepository extends JpaRepository<BrandJpaEntity, Long> {
    List<BrandJpaEntity> findByDeletedFalse();
    Optional<BrandJpaEntity> findByIdAndDeletedFalse(Long id);
    List<BrandJpaEntity> findByIdInAndDeletedFalse(List<Long> ids);
}
