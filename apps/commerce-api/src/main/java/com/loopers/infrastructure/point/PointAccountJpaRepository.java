package com.loopers.infrastructure.point;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA Repository
 * PointAccountEntity 기준
 */
public interface PointAccountJpaRepository extends JpaRepository<PointAccountEntity, Long> {
    Optional<PointAccountEntity> findByUserId(Long userId);

    /** 비관적 락 조회 (포인트 차감/충전 시 동시성 제어) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PointAccountEntity p WHERE p.userId = :userId")
    Optional<PointAccountEntity> findByUserIdForUpdate(@Param("userId") Long userId);
}
