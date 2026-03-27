package com.loopers.domain.coupon.repository;

import com.loopers.domain.coupon.model.CouponIssueRequest;
import com.loopers.domain.coupon.model.CouponIssueStatus;

import java.util.Optional;

public interface CouponIssueRequestRepository {

    CouponIssueRequest save(CouponIssueRequest request);

    Optional<CouponIssueRequest> findById(Long id);

    void updateStatus(Long id, CouponIssueStatus status);

    long countByTemplateId(Long couponTemplateId);

    boolean existsByTemplateIdAndMemberId(Long couponTemplateId, Long memberId);
}
