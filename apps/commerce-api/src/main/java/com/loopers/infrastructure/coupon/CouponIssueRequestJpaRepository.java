package com.loopers.infrastructure.coupon;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestModel, Long> {
    boolean existsByRefCouponTemplateIdAndRefMemberIdAndStatusIn(
            RefCouponTemplateId refCouponTemplateId, RefMemberId refMemberId, List<CouponIssueStatus> statuses);
    Optional<CouponIssueRequestModel> findByRequestId(String requestId);
    List<CouponIssueRequestModel> findByRequestIdInAndStatusIn(List<String> requestIds, List<CouponIssueStatus> statuses);
}
