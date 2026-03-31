package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Getter
@Entity
@Table(name = "issued_coupons", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "coupon_id"})
})
public class IssuedCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private ZonedDateTime createdAt;

    @Column(nullable = false)
    private ZonedDateTime updatedAt;

    protected IssuedCoupon() {}

    public static IssuedCoupon create(Long userId, Long couponId, LocalDateTime expiresAt) {
        IssuedCoupon issuedCoupon = new IssuedCoupon();
        issuedCoupon.userId = userId;
        issuedCoupon.couponId = couponId;
        issuedCoupon.expiresAt = expiresAt;
        ZonedDateTime now = ZonedDateTime.now();
        issuedCoupon.createdAt = now;
        issuedCoupon.updatedAt = now;
        return issuedCoupon;
    }
}
