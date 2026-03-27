package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "issued_coupons", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"coupon_id", "user_id"})
})
@Getter
public class IssuedCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    protected IssuedCoupon() {
    }

    private IssuedCoupon(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
        this.status = "AVAILABLE";
        this.createdAt = ZonedDateTime.now();
    }

    public static IssuedCoupon create(Long couponId, Long userId) {
        return new IssuedCoupon(couponId, userId);
    }
}
