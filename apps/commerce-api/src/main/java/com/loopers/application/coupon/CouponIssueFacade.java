package com.loopers.application.coupon;

import com.loopers.application.outbox.OutboxEventService;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueFacade {

    private final CouponRepository couponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final UserRepository userRepository;
    private final OutboxEventService outboxEventService;

    @Transactional
    public CouponDto.CouponIssueRequestInfo requestIssue(Long userId, Long couponId) {
        userRepository.findById(userId)
            .orElseThrow(() -> new CoreException(ErrorType.USER_NOT_FOUND));
        couponRepository.findById(couponId)
            .orElseThrow(() -> new CoreException(ErrorType.COUPON_NOT_FOUND));

        CouponIssueRequest request = CouponIssueRequest.create(couponId, userId);
        CouponIssueRequest savedRequest = couponIssueRequestRepository.save(request);

        outboxEventService.save(
            "COUPON",
            couponId,
            "COUPON_ISSUE_REQUESTED",
            Map.of("requestId", savedRequest.getId())
        );

        return CouponDto.CouponIssueRequestInfo.from(savedRequest);
    }

    @Transactional(readOnly = true)
    public CouponDto.CouponIssueRequestInfo getIssueRequestStatus(Long requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findById(requestId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));
        return CouponDto.CouponIssueRequestInfo.from(request);
    }
}
