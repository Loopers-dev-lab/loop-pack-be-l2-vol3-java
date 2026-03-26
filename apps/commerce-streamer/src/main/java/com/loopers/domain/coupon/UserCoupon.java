package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "user_coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "coupon_type", nullable = false)
    private String couponType;

    @Column(name = "value", nullable = false)
    private int value;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "version")
    private Long version;

    private UserCoupon(Long userId, Coupon coupon) {
        this.userId = userId;
        this.couponTemplateId = coupon.getId();
        this.name = coupon.name();
        this.couponType = coupon.couponType();
        this.value = coupon.value();
        this.expiredAt = coupon.expiredAt();
        this.status = "AVAILABLE";
        this.version = 0L;
    }

    public static UserCoupon of(Long userId, Coupon coupon) {
        return new UserCoupon(userId, coupon);
    }
}
