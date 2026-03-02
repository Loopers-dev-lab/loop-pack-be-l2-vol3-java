package com.loopers.infrastructure.point;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA Repository
 * PointAccountEntity 기준
 */
public interface PointAccountJpaRepository extends JpaRepository<PointAccountEntity, Long> {
    Optional<PointAccountEntity> findByUserId(Long userId);
}
