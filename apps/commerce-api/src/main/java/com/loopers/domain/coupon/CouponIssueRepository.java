package com.loopers.domain.coupon;

import com.loopers.domain.PageResult;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRepository {

    CouponIssue save(CouponIssue couponIssue);

    Optional<CouponIssue> findById(Long id);

    boolean existsByCouponIdAndUserId(Long couponId, Long userId);

    List<CouponIssue> findAllByUserId(Long userId);

    PageResult<CouponIssue> findByCouponId(Long couponId, int page, int size);
}
