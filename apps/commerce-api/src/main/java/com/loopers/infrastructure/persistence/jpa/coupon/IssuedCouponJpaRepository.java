package com.loopers.infrastructure.persistence.jpa.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * IssuedCoupon JPA Repository.
 */
public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ic FROM IssuedCouponJpaEntity ic WHERE ic.id = :id")
    Optional<IssuedCouponJpaEntity> findByIdWithLock(@Param("id") Long id);

    Optional<IssuedCouponJpaEntity> findByIdAndUserId(Long id, Long userId);

    List<IssuedCouponJpaEntity> findAllByUserId(Long userId, Pageable pageable);

    boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);

    long countByUserId(Long userId);

    List<IssuedCouponJpaEntity> findAllByCouponTemplateId(Long couponTemplateId, Pageable pageable);

    long countByCouponTemplateId(Long couponTemplateId);
}
