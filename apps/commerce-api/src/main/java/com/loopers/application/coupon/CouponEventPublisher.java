package com.loopers.application.coupon;

import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;

public interface CouponEventPublisher {
    void publish(CouponIssueRequestedEvent event);
}
