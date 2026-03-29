package com.loopers.contract.coupon;

import java.time.LocalDateTime;
import java.util.UUID;

public record CouponIssueRequestedEvent(
        UUID requestId,
        UUID couponId,
        String memberId,
        LocalDateTime requestedAt
) {
}
