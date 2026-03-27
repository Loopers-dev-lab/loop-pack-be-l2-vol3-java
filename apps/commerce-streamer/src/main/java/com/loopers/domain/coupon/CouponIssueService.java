package com.loopers.domain.coupon;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private final CouponTemplateRepository couponTemplateRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public void processIssue(String requestId, Long couponTemplateId, Long memberId) {
        CouponIssueRequestModel request = couponIssueRequestRepository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("발급 요청을 찾을 수 없습니다: " + requestId));

        if (userCouponRepository.existsByRefMemberIdAndRefCouponTemplateId(memberId, couponTemplateId)) {
            request.markAsRejected();
            return;
        }

        long affected = couponTemplateRepository.decreaseStockIfAvailable(couponTemplateId);

        if (affected > 0) {
            userCouponRepository.save(UserCouponModel.create(memberId, couponTemplateId));
            request.markAsIssued();
        } else {
            request.markAsRejected();
        }
    }
}
