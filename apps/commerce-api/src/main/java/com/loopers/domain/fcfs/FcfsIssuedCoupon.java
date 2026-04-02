package com.loopers.domain.fcfs;

import java.time.Instant;
import java.util.Objects;

/**
 * 발급된 선착순 쿠폰 도메인 모델.
 */
public class FcfsIssuedCoupon {

    private final Long id;
    private final Long couponId;
    private final Long userId;
    private final Instant issuedAt;

    private FcfsIssuedCoupon(Long id, Long couponId, Long userId, Instant issuedAt) {
        this.id = id;
        this.couponId = Objects.requireNonNull(couponId, "couponId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.issuedAt = issuedAt;
    }

    public static FcfsIssuedCoupon create(Long couponId, Long userId) {
        return new FcfsIssuedCoupon(null, couponId, userId, Instant.now());
    }

    public static FcfsIssuedCoupon reconstitute(Long id, Long couponId, Long userId, Instant issuedAt) {
        return new FcfsIssuedCoupon(id, couponId, userId, issuedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getCouponId() {
        return couponId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}
