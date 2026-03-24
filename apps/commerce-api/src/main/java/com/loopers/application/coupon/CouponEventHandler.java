package com.loopers.application.coupon;

import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class CouponEventHandler {

    private final CouponEventPublisher couponEventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCouponIssueRequested(CouponIssueRequestedEvent event) {
        couponEventPublisher.publish(event);
    }
}
