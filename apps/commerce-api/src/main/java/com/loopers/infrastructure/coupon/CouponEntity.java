package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
public class CouponEntity extends BaseEntity {

    @Getter
    @Column(nullable = false)
    private String name;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Getter
    @Column(nullable = false)
    private int value;

    @Getter
    @Column(name = "min_order_amount", nullable = false)
    private int minOrderAmount;

    @Getter
    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Getter
    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Getter
    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    protected CouponEntity() {
    }

    public CouponEntity(String name, CouponType type, int value, int minOrderAmount, int totalQuantity, int remainingQuantity, LocalDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = remainingQuantity;
        this.expiredAt = expiredAt;
    }

    public static CouponEntity from(Coupon coupon) {
        return new CouponEntity(
                coupon.name(),
                coupon.type(),
                coupon.value(),
                coupon.minOrderAmount(),
                coupon.totalQuantity(),
                coupon.remainingQuantity(),
                coupon.expiredAt()
        );
    }

    public Coupon toDomain() {
        return new Coupon(getId(), name, type, value, minOrderAmount, totalQuantity, remainingQuantity, expiredAt);
    }

    public void updateFrom(Coupon coupon) {
        this.name = coupon.name();
        this.type = coupon.type();
        this.value = coupon.value();
        this.minOrderAmount = coupon.minOrderAmount();
        this.totalQuantity = coupon.totalQuantity();
        this.remainingQuantity = coupon.remainingQuantity();
        this.expiredAt = coupon.expiredAt();
    }
}
