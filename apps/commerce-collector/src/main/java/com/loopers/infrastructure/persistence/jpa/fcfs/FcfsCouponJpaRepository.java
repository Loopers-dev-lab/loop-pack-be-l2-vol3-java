package com.loopers.infrastructure.persistence.jpa.fcfs;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 선착순 쿠폰 JPA Repository (commerce-collector용).
 */
public interface FcfsCouponJpaRepository extends JpaRepository<FcfsCouponJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM FcfsCouponJpaEntity c WHERE c.id = :id")
    Optional<FcfsCouponJpaEntity> findByIdWithLock(@Param("id") Long id);

    @Modifying
    @Query("UPDATE FcfsCouponJpaEntity c SET c.issuedCount = c.issuedCount + 1 WHERE c.id = :id")
    void incrementIssuedCount(@Param("id") Long id);
}
