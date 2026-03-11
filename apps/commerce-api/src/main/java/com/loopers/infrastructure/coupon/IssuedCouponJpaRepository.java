package com.loopers.infrastructure.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponEntity, Long> {
    long countByCouponTemplateId(Long couponTemplateId);
    long countByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId);
    List<IssuedCouponEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);
    List<IssuedCouponEntity> findAllByCouponTemplateIdOrderByCreatedAtDesc(Long couponTemplateId);

    /** 원자적 UPDATE: status=ISSUED인 경우만 USED로 변경 (동시성 제어) */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE IssuedCouponEntity e SET e.status = 'USED', e.orderId = :orderId, e.usedAt = :usedAt, e.updatedAt = :usedAt WHERE e.id = :id AND e.status = 'ISSUED'")
    int useAtomically(@Param("id") Long id, @Param("orderId") Long orderId, @Param("usedAt") ZonedDateTime usedAt);

    /** 원자적 복원: status=USED이고 해당 orderId인 경우만 ISSUED로 되돌림 (보상 트랜잭션용) */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE IssuedCouponEntity e SET e.status = 'ISSUED', e.orderId = NULL, e.usedAt = NULL, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :id AND e.status = 'USED' AND e.orderId = :orderId")
    int restoreAtomically(@Param("id") Long id, @Param("orderId") Long orderId);
}
