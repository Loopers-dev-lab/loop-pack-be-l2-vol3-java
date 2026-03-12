package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueModel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponIssueJpaRepository extends JpaRepository<CouponIssueModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CouponIssueModel ci WHERE ci.id = :id")
    Optional<CouponIssueModel> findByIdForUpdate(@Param("id") Long id);

    List<CouponIssueModel> findAllByUserId(Long userId);

    Page<CouponIssueModel> findAllByCouponId(Long couponId, Pageable pageable);

    Optional<CouponIssueModel> findByUserIdAndCouponId(Long userId, Long couponId);
}
