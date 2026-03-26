package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponActionStatus;
import com.loopers.domain.coupon.CouponActionType;
import com.loopers.domain.coupon.CouponPendingActionModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 비동기 액션 JPA 레포지토리.
 */
public interface CouponPendingActionJpaRepository extends JpaRepository<CouponPendingActionModel, Long> {

    @Query("SELECT a FROM CouponPendingActionModel a WHERE a.status = :status ORDER BY a.createdAt ASC")
    List<CouponPendingActionModel> findByStatusOrderByCreatedAtAsc(@Param("status") CouponActionStatus status,
                                                                    org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("UPDATE CouponPendingActionModel a SET a.status = 'CANCELLED', a.processedAt = CURRENT_TIMESTAMP " +
           "WHERE a.userCouponId = :userCouponId AND a.actionType = :actionType AND a.status = 'PENDING'")
    int cancelPendingByUserCouponIdAndType(@Param("userCouponId") Long userCouponId,
                                           @Param("actionType") CouponActionType actionType);

    @Modifying
    @Query("DELETE FROM CouponPendingActionModel a WHERE a.status = :status AND a.createdAt < :threshold")
    int deleteByStatusAndCreatedAtBefore(@Param("status") CouponActionStatus status,
                                         @Param("threshold") LocalDateTime threshold);
}
