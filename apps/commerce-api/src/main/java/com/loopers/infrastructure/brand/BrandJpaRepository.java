package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA Repository
 * BrandEntity 기준
 */
public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {
    List<BrandEntity> findAllByStatusAndDeletedAtIsNull(BrandStatus status);
    List<BrandEntity> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
}
