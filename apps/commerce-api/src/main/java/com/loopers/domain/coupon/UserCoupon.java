package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "user_coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "coupon_template_id")
    private Long couponTemplateId;

    @Column(name = "name")
    private String name;

    @Column(name = "coupon_type")
    @Enumerated(EnumType.STRING)
    private CouponType couponType;

    @Column(name = "value")
    private int value;

    @Column(name = "expired_at")
    private ZonedDateTime expiredAt;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private CouponStatus status;

    @Version
    private Long version;

    private UserCoupon(Long userId, Long couponTemplateId, String name, CouponType couponType, int value, ZonedDateTime expiredAt) {
        this.userId = userId;
        this.couponTemplateId = couponTemplateId;
        this.name = name;
        this.couponType = couponType;
        this.value = value;
        this.expiredAt = expiredAt;
        this.status = CouponStatus.AVAILABLE;
    }

    public static UserCoupon of(Coupon coupon, Long userId) {
        if (coupon == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰은 필수입니다.");
        }

        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자ID는 필수입니다.");
        }

        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }

        return new UserCoupon(userId,
                coupon.getId(),
                coupon.name(),
                coupon.couponType(),
                coupon.value(),
                coupon.expiredAt());
    }

    public Long userId() {
        return userId;
    }

    public String name() {
        return name;
    }

    public CouponType couponType() {
        return couponType;
    }

    public int value() {
        return value;
    }

    public CouponStatus status() {
        if (status == CouponStatus.AVAILABLE && expiredAt.isBefore(ZonedDateTime.now())) {
            return CouponStatus.EXPIRED;
        }

        return status;
    }

    public long calculateDiscount(long amount) {
        return couponType.calculate(amount, value);
    }

    public void validate(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "본인의 쿠폰만 사용할 수 있습니다.");
        }
        if (status == CouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        if (status() == CouponStatus.EXPIRED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
    }

    public void use(Long userId) {
        validate(userId);
        status = CouponStatus.USED;
    }
}
