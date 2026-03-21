package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponEntity, UUID> {
    Optional<IssuedCouponEntity> findByMemberIdAndCouponId(String memberId, UUID couponId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IssuedCouponEntity ic
               set ic.status = :usedStatus,
                   ic.usedAt = :usedAt
             where ic.memberId = :memberId
               and ic.couponId = :couponId
               and ic.status = :availableStatus
               and ic.expiredAt >= :now
            """)
    int markUsedAtomically(
            @Param("memberId") String memberId,
            @Param("couponId") UUID couponId,
            @Param("availableStatus") CouponStatus availableStatus,
            @Param("usedStatus") CouponStatus usedStatus,
            @Param("now") LocalDateTime now,
            @Param("usedAt") LocalDateTime usedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update IssuedCouponEntity ic
               set ic.status = :availableStatus,
                   ic.usedAt = null
             where ic.memberId = :memberId
               and ic.couponId = :couponId
               and ic.status = :usedStatus
            """)
    int markAvailableAtomically(
            @Param("memberId") String memberId,
            @Param("couponId") UUID couponId,
            @Param("availableStatus") CouponStatus availableStatus,
            @Param("usedStatus") CouponStatus usedStatus
    );

    List<IssuedCouponEntity> findByMemberIdOrderByCreatedAtDesc(String memberId);

    Page<IssuedCouponEntity> findByCouponIdOrderByCreatedAtDesc(UUID couponId, Pageable pageable);
}
