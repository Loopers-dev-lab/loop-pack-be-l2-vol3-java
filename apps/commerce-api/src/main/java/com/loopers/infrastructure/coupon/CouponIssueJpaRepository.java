package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponIssueJpaRepository extends JpaRepository<CouponIssue, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CouponIssue ci WHERE ci.id = :id")
    Optional<CouponIssue> findByIdWithLock(@Param("id") Long id);

    List<CouponIssue> findAllByMemberId(Long memberId);
    List<CouponIssue> findAllByCouponId(Long couponId);
}
