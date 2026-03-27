package com.loopers.application.coupon;

import com.loopers.application.outbox.OutboxEventPublisher;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponPromotion;
import com.loopers.domain.coupon.CouponPromotionRepository;
import com.loopers.event.EventType;
import com.loopers.event.payload.CouponIssueRequestedEventPayload;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class CouponIssueRequestFacade {

    private final CouponPromotionRepository couponPromotionRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponIssueCountManager couponIssueCountManager;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public CouponIssueRequestInfo issueRequest(Long userId, Long couponId) {
        CouponPromotion promotion = couponPromotionRepository.findByCouponId(couponId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "선착순 프로모션 대상 쿠폰이 아닙니다."));

        promotion.validateIssuable();

        long issuedCount = couponIssueCountManager.increment(couponId);
        if (issuedCount > promotion.getMaxQuantity()) {
            couponIssueCountManager.decrement(couponId);
            throw new CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰이 모두 소진되었습니다.");
        }

        CouponIssueRequest request;
        try {
            request = couponIssueRequestRepository.save(CouponIssueRequest.create(couponId, userId));
        } catch (DataIntegrityViolationException e) {
            couponIssueCountManager.decrement(couponId);
            throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청한 쿠폰입니다.");
        }

        outboxEventPublisher.publish(
                EventType.COUPON_ISSUE_REQUESTED,
                CouponIssueRequestedEventPayload.of(request.getId(), couponId, userId),
                couponId
        );

        return CouponIssueRequestInfo.from(request);
    }

    @Transactional(readOnly = true)
    public CouponIssueRequestInfo getIssueRequest(Long userId, Long requestId) {
        CouponIssueRequest request = couponIssueRequestRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "발급 요청을 찾을 수 없습니다."));
        return CouponIssueRequestInfo.from(request);
    }
}
