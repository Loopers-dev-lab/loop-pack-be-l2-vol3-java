package com.loopers.infrastructure.persistence.jpa.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * CouponTemplate JPA Repository.
 */
public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateJpaEntity, Long> {

    Optional<CouponTemplateJpaEntity> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ct FROM CouponTemplateJpaEntity ct WHERE ct.id = :id AND ct.deletedAt IS NULL")
    Optional<CouponTemplateJpaEntity> findByIdWithLock(@Param("id") Long id);

    Page<CouponTemplateJpaEntity> findAllByDeletedAtIsNull(Pageable pageable);
}
