package com.loopers.application.handler;

import com.loopers.application.CouponIssueProcessor;
import com.loopers.application.EventHandler;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.CouponIssueRequestedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponIssueRequestedEventHandler implements EventHandler<CouponIssueRequestedEventPayload> {

    private final CouponIssueProcessor couponIssueProcessor;

    @Override
    public boolean supports(Event<EventPayload> event) {
        return event.getType() == EventType.COUPON_ISSUE_REQUESTED;
    }

    @Override
    public void handle(Event<CouponIssueRequestedEventPayload> event) {
        CouponIssueRequestedEventPayload payload = event.getPayload();
        couponIssueProcessor.process(
            payload.getCouponIssueRequestId(),
            payload.getCouponId(),
            payload.getUserId()
        );
    }
}
