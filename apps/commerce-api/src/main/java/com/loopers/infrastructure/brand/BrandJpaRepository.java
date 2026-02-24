package com.loopers.infrastructure.brand;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA Repository
 * BrandEntity 기준
 */
public interface BrandJpaRepository extends JpaRepository<BrandEntity, Long> {
    List<BrandEntity> findAllByStatusAndDeletedAtIsNull(String status);
}
