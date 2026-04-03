package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
public class CouponEntity extends BaseEntity {

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    protected CouponEntity() {
    }

    public CouponEntity(LocalDateTime expiredAt, int totalQuantity, int remainingQuantity) {
        this.expiredAt = expiredAt;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = remainingQuantity;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }
}
