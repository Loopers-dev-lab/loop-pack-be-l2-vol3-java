package com.loopers.application.collector;

import com.loopers.domain.collector.CollectorCouponIssueRequestModel;
import com.loopers.domain.collector.CollectorCouponIssueRequestStatus;
import com.loopers.domain.collector.CollectorCouponModel;
import com.loopers.domain.collector.CollectorUserCouponModel;
import com.loopers.infrastructure.collector.CollectorCouponIssueRequestJpaRepository;
import com.loopers.infrastructure.collector.CollectorCouponJpaRepository;
import com.loopers.infrastructure.collector.CollectorUserCouponJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponIssueConsumeService {

    private final CollectorCouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
    private final CollectorCouponJpaRepository couponJpaRepository;
    private final CollectorUserCouponJpaRepository userCouponJpaRepository;

    @Transactional
    public void handleRequest(Long requestId, Long couponId, Long userId) {
        CollectorCouponIssueRequestModel request = couponIssueRequestJpaRepository.findByIdForUpdate(requestId)
            .orElse(null);

        if (request == null || request.getStatus() != CollectorCouponIssueRequestStatus.REQUESTED) {
            return;
        }

        CollectorCouponModel coupon = couponJpaRepository.findByIdForUpdate(couponId).orElse(null);
        if (coupon == null) {
            request.fail("쿠폰을 찾을 수 없습니다.");
            return;
        }

        if (!coupon.canIssue()) {
            request.fail("쿠폰 발급 가능 상태가 아닙니다.");
            return;
        }

        if (userCouponJpaRepository.existsByUserIdAndCoupon_Id(userId, couponId)) {
            request.fail("이미 발급된 쿠폰입니다.");
            return;
        }

        coupon.reserveIssue();
        userCouponJpaRepository.save(new CollectorUserCouponModel(userId, coupon));
        request.succeed();
    }
}
