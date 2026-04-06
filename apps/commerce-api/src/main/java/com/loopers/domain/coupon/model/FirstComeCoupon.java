package com.loopers.domain.coupon.model;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class FirstComeCoupon {

    private Long id;
    private Long couponTemplateId;
    private int maxQuantity;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    private FirstComeCoupon(Long couponTemplateId, int maxQuantity, LocalDateTime startAt, LocalDateTime endAt) {
        this.couponTemplateId = couponTemplateId;
        this.maxQuantity = maxQuantity;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public static FirstComeCoupon create(Long couponTemplateId, int maxQuantity, LocalDateTime startAt, LocalDateTime endAt) {
        return new FirstComeCoupon(couponTemplateId, maxQuantity, startAt, endAt);
    }

    public static FirstComeCoupon reconstruct(Long id, Long couponTemplateId, int maxQuantity,
                                               LocalDateTime startAt, LocalDateTime endAt) {
        FirstComeCoupon fc = new FirstComeCoupon(couponTemplateId, maxQuantity, startAt, endAt);
        fc.id = id;
        return fc;
    }

    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startAt) && now.isBefore(endAt);
    }
}
