package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "coupon_promotions")
public class CouponPromotion extends BaseEntity {

    @Column(name = "coupon_id", nullable = false, unique = true)
    private Long couponId;

    @Column(name = "max_quantity", nullable = false)
    private int maxQuantity;

    @Column(name = "started_at", nullable = false)
    private ZonedDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private ZonedDateTime endedAt;

    protected CouponPromotion() {}

    private CouponPromotion(Long couponId, int maxQuantity, ZonedDateTime startedAt, ZonedDateTime endedAt) {
        this.couponId = couponId;
        this.maxQuantity = maxQuantity;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
    }

    public static CouponPromotion create(Long couponId, int maxQuantity, ZonedDateTime startedAt, ZonedDateTime endedAt) {
        return new CouponPromotion(couponId, maxQuantity, startedAt, endedAt);
    }

    public void validateIssuable() {
        ZonedDateTime now = ZonedDateTime.now();
        if (now.isBefore(startedAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "아직 시작되지 않은 프로모션입니다.");
        }
        if (now.isAfter(endedAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "종료된 프로모션입니다.");
        }
    }
}
