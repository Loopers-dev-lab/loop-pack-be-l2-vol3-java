package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "user_coupons")
public class UserCoupon extends BaseEntity {

    @Getter
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Getter
    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private CouponStatus status;

    @Getter
    @Column(name = "issued_at", nullable = false)
    private ZonedDateTime issuedAt;

    @Getter
    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Getter
    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    protected UserCoupon() {}

    public UserCoupon(Long userId, Long couponTemplateId, Integer validDays) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "userId는 필수입니다");
        }
        if (couponTemplateId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "couponTemplateId는 필수입니다");
        }
        if (validDays == null || validDays < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효 기간은 0 이상이어야 합니다");
        }
        this.userId = userId;
        this.couponTemplateId = couponTemplateId;
        this.status = CouponStatus.AVAILABLE;
        this.issuedAt = ZonedDateTime.now();
        this.expiredAt = this.issuedAt.plusDays(validDays);
    }

    public void use() {
        if (this.status == CouponStatus.USED) {
            throw new CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다");
        }
        if (this.status == CouponStatus.EXPIRED || !ZonedDateTime.now().isBefore(this.expiredAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다");
        }
        this.status = CouponStatus.USED;
        this.usedAt = ZonedDateTime.now();
    }

    public boolean isUsable() {
        return this.status == CouponStatus.AVAILABLE
            && ZonedDateTime.now().isBefore(this.expiredAt);
    }
}
