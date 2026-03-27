package com.loopers.domain.event;

import java.time.ZonedDateTime;
import java.util.Objects;

public record CouponIssueRequestPayload(
    String requestId,
    Long couponId,
    Long userId,
    ZonedDateTime occurredAt
) {
    public CouponIssueRequestPayload {
        Objects.requireNonNull(requestId, "requestId는 필수입니다");
        Objects.requireNonNull(couponId, "couponId는 필수입니다");
        Objects.requireNonNull(userId, "userId는 필수입니다");
    }
}
