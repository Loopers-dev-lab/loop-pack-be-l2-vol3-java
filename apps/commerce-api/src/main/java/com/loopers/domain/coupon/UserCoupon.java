package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    private UserCoupon(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
        this.status = CouponStatus.AVAILABLE;
    }

    public static UserCoupon create(Long userId, Long couponId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 필수입니다.");
        }
        return new UserCoupon(userId, couponId);
    }

    public void use() {
        if (this.status != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
        this.status = CouponStatus.USED;
    }

    public void restore() {
        if (this.status != CouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복원할 수 없는 쿠폰 상태입니다.");
        }
        this.status = CouponStatus.AVAILABLE;
    }

    public void validateUsable(Long requestUserId) {
        if (!this.userId.equals(requestUserId)) {
            throw new CoreException(ErrorType.FORBIDDEN, "다른 사용자의 쿠폰은 사용할 수 없습니다.");
        }
        if (this.status != CouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
    }
}
