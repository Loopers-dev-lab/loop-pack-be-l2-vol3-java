package com.loopers.domain.coupon;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "owned_coupon", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"coupon_id", "user_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OwnedCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Coupon coupon;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Version
    private Long version;

    public static OwnedCoupon create(Coupon coupon, Long userId) {
        if (coupon.isExpired()) {
            throw new IllegalStateException("쿠폰이 만료되었습니다: couponId=" + coupon.getId());
        }
        OwnedCoupon ownedCoupon = new OwnedCoupon();
        ownedCoupon.coupon = coupon;
        ownedCoupon.userId = userId;
        return ownedCoupon;
    }

    @PrePersist
    private void prePersist() {
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
}
