package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponStatus;
import com.loopers.domain.coupon.IssuedCoupon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "issued_coupons", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "coupon_id"}))
public class IssuedCouponEntity extends BaseEntity {

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    protected IssuedCouponEntity() {
    }

    public IssuedCouponEntity(String memberId, UUID couponId, CouponStatus status,
                              LocalDateTime issuedAt, LocalDateTime expiredAt, LocalDateTime usedAt) {
        this.memberId = memberId;
        this.couponId = couponId;
        this.status = status;
        this.issuedAt = issuedAt;
        this.expiredAt = expiredAt;
        this.usedAt = usedAt;
    }

    public static IssuedCouponEntity from(IssuedCoupon issuedCoupon) {
        return new IssuedCouponEntity(
                issuedCoupon.memberId(),
                issuedCoupon.couponId(),
                issuedCoupon.status(),
                issuedCoupon.issuedAt(),
                issuedCoupon.expiredAt(),
                issuedCoupon.usedAt()
        );
    }

    public IssuedCoupon toDomain() {
        return new IssuedCoupon(memberId, couponId, status, issuedAt, expiredAt, usedAt);
    }

    public void updateFrom(IssuedCoupon issuedCoupon) {
        this.status = issuedCoupon.status();
        this.expiredAt = issuedCoupon.expiredAt();
        this.usedAt = issuedCoupon.usedAt();
    }

    public UUID getCouponId() {
        return couponId;
    }
}
