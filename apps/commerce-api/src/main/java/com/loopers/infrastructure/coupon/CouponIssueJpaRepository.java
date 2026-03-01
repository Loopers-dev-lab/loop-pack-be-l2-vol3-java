package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponIssueJpaRepository extends JpaRepository<CouponIssue, Long> {

    Optional<CouponIssue> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByCouponIdAndUserIdAndDeletedAtIsNull(Long couponId, Long userId);

    List<CouponIssue> findAllByUserIdAndDeletedAtIsNull(Long userId);

    Page<CouponIssue> findByCouponIdAndDeletedAtIsNull(Long couponId, Pageable pageable);
}
