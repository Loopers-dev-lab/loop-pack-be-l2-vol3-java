package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZonedDateTime;

@Entity
@Table(name = "issued_coupon", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"member_id", "coupon_template_id"})
})
public class IssuedCoupon extends BaseEntity {

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    protected IssuedCoupon() {}

    public IssuedCoupon(Long couponTemplateId, Long memberId) {
        validateCouponTemplateId(couponTemplateId);
        validateMemberId(memberId);
        this.couponTemplateId = couponTemplateId;
        this.memberId = memberId;
        this.status = CouponStatus.AVAILABLE;
    }

    private void validateCouponTemplateId(Long couponTemplateId) {
        if (couponTemplateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 ID는 필수입니다.");
        }
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
    }

    public void use() {
        if (status == CouponStatus.USED) {
            throw new CoreException(ErrorType.COUPON_ALREADY_USED);
        }
        if (status == CouponStatus.EXPIRED) {
            throw new CoreException(ErrorType.COUPON_EXPIRED);
        }
        this.status = CouponStatus.USED;
        this.usedAt = ZonedDateTime.now();
    }

    public boolean isUsable() {
        return status == CouponStatus.AVAILABLE;
    }

    public void validateOwnership(Long requestMemberId) {
        if (!this.memberId.equals(requestMemberId)) {
            throw new CoreException(ErrorType.COUPON_NOT_OWNED);
        }
    }

    public Long getCouponTemplateId() { return couponTemplateId; }
    public Long getMemberId() { return memberId; }
    public CouponStatus getStatus() { return status; }
    public ZonedDateTime getUsedAt() { return usedAt; }
}
