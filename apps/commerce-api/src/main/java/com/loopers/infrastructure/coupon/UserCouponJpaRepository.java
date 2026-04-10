package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.UserCouponModel;
import com.loopers.support.enums.UserCouponStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * 사용자 발급 쿠폰 JPA 레포지토리.
 */
public interface UserCouponJpaRepository extends JpaRepository<UserCouponModel, Long> {

    /**
     * 비관적 쓰기 락(SELECT FOR UPDATE)으로 발급 쿠폰을 조회한다.
     * 동시 주문 시 쿠폰 중복 사용 방지를 위해 사용된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT uc FROM UserCouponModel uc WHERE uc.userCouponId = :userCouponId")
    Optional<UserCouponModel> findByIdWithLock(@Param("userCouponId") Long userCouponId);

    Optional<UserCouponModel> findByUserIdAndCouponId(Long userId, Long couponId);

    List<UserCouponModel> findAllByUserId(Long userId);

    List<UserCouponModel> findAllByCouponId(Long couponId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    Page<UserCouponModel> findAllByCouponId(Long couponId, Pageable pageable);

    Optional<UserCouponModel> findByOrderId(Long orderId);

    @Modifying
    @Query("UPDATE UserCouponModel u SET u.status = :newStatus " +
           "WHERE u.userCouponId = :userCouponId AND u.status = :expectedStatus")
    int updateStatusCas(@Param("userCouponId") Long userCouponId,
                        @Param("expectedStatus") UserCouponStatus expectedStatus,
                        @Param("newStatus") UserCouponStatus newStatus);
}
