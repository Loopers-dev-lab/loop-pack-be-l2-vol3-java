package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CouponTemplateJpaRepository extends JpaRepository<CouponTemplateEntity, Long> {
    List<CouponTemplateEntity> findAllByIdIn(Set<Long> ids);

    /** 비관적 락 조회 (쿠폰 발급 시 동시성 제어) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("SELECT t FROM CouponTemplateEntity t WHERE t.id = :id")
    Optional<CouponTemplateEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT t FROM CouponTemplateEntity t WHERE t.status = :status AND t.deletedAt IS NULL " +
            "AND t.validFrom <= :now AND t.validTo >= :now ORDER BY t.createdAt DESC")
    List<CouponTemplateEntity> findAllIssuable(@Param("status") CouponTemplateStatus status,
                                                @Param("now") ZonedDateTime now);
}
