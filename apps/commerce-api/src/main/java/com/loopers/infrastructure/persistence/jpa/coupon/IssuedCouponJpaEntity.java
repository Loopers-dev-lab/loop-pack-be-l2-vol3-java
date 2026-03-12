package com.loopers.infrastructure.persistence.jpa.coupon;

import com.loopers.domain.coupon.IssuedCouponStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 발급된 쿠폰 JPA 엔티티.
 */
@Entity
@Table(
    name = "issued_coupons",
    indexes = {
        @Index(name = "idx_issued_coupons_user_id_status", columnList = "user_id, status"),
        @Index(name = "idx_issued_coupons_user_template", columnList = "user_id, coupon_template_id")
    }
)
public class IssuedCouponJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IssuedCouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private ZonedDateTime issuedAt;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected IssuedCouponJpaEntity() {}

    public IssuedCouponJpaEntity(Long userId, Long couponTemplateId,
            IssuedCouponStatus status, ZonedDateTime expiredAt) {
        this.userId = userId;
        this.couponTemplateId = couponTemplateId;
        this.status = status;
        this.expiredAt = expiredAt;
    }

    @PrePersist
    private void prePersist() {
        this.issuedAt = ZonedDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponTemplateId() {
        return couponTemplateId;
    }

    public IssuedCouponStatus getStatus() {
        return status;
    }

    public ZonedDateTime getUsedAt() {
        return usedAt;
    }

    public ZonedDateTime getIssuedAt() {
        return issuedAt;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public void setStatus(IssuedCouponStatus status) {
        this.status = status;
    }

    public void setUsedAt(ZonedDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
