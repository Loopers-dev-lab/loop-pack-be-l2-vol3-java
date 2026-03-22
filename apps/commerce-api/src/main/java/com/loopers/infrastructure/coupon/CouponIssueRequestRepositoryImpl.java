package com.loopers.infrastructure.coupon;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.coupon.CouponIssueRequestModel;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.vo.RefCouponTemplateId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Override
    public boolean existsByRefCouponTemplateIdAndRefMemberIdAndStatusIn(
            RefCouponTemplateId refCouponTemplateId, RefMemberId refMemberId, List<CouponIssueStatus> statuses) {
        return couponIssueRequestJpaRepository.existsByRefCouponTemplateIdAndRefMemberIdAndStatusIn(
                refCouponTemplateId, refMemberId, statuses);
    }

    @Override
    public CouponIssueRequestModel save(CouponIssueRequestModel model) {
        return couponIssueRequestJpaRepository.save(model);
    }

    @Override
    public Optional<CouponIssueRequestModel> findByRequestId(String requestId) {
        return couponIssueRequestJpaRepository.findByRequestId(requestId);
    }

    @Override
    public List<CouponIssueRequestModel> findByRequestIdInAndStatusIn(
            List<String> requestIds, List<CouponIssueStatus> statuses) {
        return couponIssueRequestJpaRepository.findByRequestIdInAndStatusIn(requestIds, statuses);
    }
}
