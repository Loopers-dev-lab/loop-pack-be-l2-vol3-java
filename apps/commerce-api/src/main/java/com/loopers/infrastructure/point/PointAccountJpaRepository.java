package com.loopers.infrastructure.point;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Spring Data JPA Repository
 * PointAccountEntity 기준
 */
public interface PointAccountJpaRepository extends JpaRepository<PointAccountEntity, Long> {
    Optional<PointAccountEntity> findByUserId(Long userId);

    /** 원자적 차감: balance >= amount 조건으로 동시성 보호 */
    @Modifying
    @Query("UPDATE PointAccountEntity p SET p.balance = p.balance - :amount, p.updatedAt = CURRENT_TIMESTAMP WHERE p.userId = :userId AND p.balance >= :amount")
    int useAtomically(@Param("userId") Long userId, @Param("amount") int amount);

    /** 원자적 충전 */
    @Modifying
    @Query("UPDATE PointAccountEntity p SET p.balance = p.balance + :amount, p.updatedAt = CURRENT_TIMESTAMP WHERE p.userId = :userId")
    int chargeAtomically(@Param("userId") Long userId, @Param("amount") int amount);

    /** 원자적 적립 */
    @Modifying
    @Query("UPDATE PointAccountEntity p SET p.balance = p.balance + :amount, p.updatedAt = CURRENT_TIMESTAMP WHERE p.userId = :userId")
    int earnAtomically(@Param("userId") Long userId, @Param("amount") int amount);
}
