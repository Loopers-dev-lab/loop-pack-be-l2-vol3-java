package com.loopers.infrastructure.brand;

import com.loopers.infrastructure.brand.BrandJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BrandJpaRepository extends JpaRepository<BrandJpaEntity, Long> {

    boolean existsByName(String name);

    List<BrandJpaEntity> findAllByDeletedAtIsNull();

    List<BrandJpaEntity> findAllByIdIn(List<Long> ids);
}
