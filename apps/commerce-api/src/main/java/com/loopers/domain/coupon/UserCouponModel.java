package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCouponModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    private CouponModel coupon;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserCouponStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Column(name = "order_id")
    private Long orderId;

    public UserCouponModel(Long userId, CouponModel coupon) {
        validate(userId, coupon);
        this.userId = userId;
        this.coupon = coupon;
        this.status = UserCouponStatus.AVAILABLE;
    }

    public UserCouponStatus getDisplayStatus() {
        if (status == UserCouponStatus.AVAILABLE && coupon.isExpired()) {
            return UserCouponStatus.EXPIRED;
        }
        return status;
    }

    public long calculateDiscountAmount(long orderAmount) {
        validateUsable();
        return coupon.calculateDiscount(orderAmount);
    }

    public void use(Long orderId, long orderAmount) {
        validateUsable();
        coupon.calculateDiscount(orderAmount);

        this.status = UserCouponStatus.USED;
        this.usedAt = ZonedDateTime.now();
        this.orderId = orderId;
    }

    private void validateUsable() {
        if (status == UserCouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다.");
        }
        if (status == UserCouponStatus.EXPIRED || coupon.isExpired()) {
            this.status = UserCouponStatus.EXPIRED;
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
    }

    private void validate(Long userId, CouponModel coupon) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (coupon == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰은 필수입니다.");
        }
    }
}
