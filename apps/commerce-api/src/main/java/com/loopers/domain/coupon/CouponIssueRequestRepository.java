package com.loopers.domain.coupon;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRequestRepository {
    boolean existsByRefCouponTemplateIdAndRefMemberIdAndStatusIn(
            RefCouponTemplateId refCouponTemplateId, RefMemberId refMemberId, List<CouponIssueStatus> statuses);
    CouponIssueRequestModel save(CouponIssueRequestModel model);
    CouponIssueRequestModel saveAndFlush(CouponIssueRequestModel model);
    Optional<CouponIssueRequestModel> findByRequestId(String requestId);
    List<CouponIssueRequestModel> findByRequestIdInAndStatusIn(List<String> requestIds, List<CouponIssueStatus> statuses);
}
