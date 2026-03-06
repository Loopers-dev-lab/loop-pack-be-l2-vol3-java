package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "issued_coupons", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"coupon_id", "user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IssuedCouponStatus status;

    @Column(name = "used_order_id")
    private Long usedOrderId;

    @Column(name = "issued_at", nullable = false)
    private ZonedDateTime issuedAt;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "discount_value", nullable = false))
    private Money discountValue;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "min_order_amount", nullable = false))
    private Money minOrderAmount;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "max_discount_amount"))
    private Money maxDiscountAmount;

    private IssuedCoupon(Long id, Long couponId, Long userId, IssuedCouponStatus status,
                         Long usedOrderId, ZonedDateTime issuedAt, ZonedDateTime expiredAt, ZonedDateTime usedAt,
                         DiscountType discountType, Money discountValue, Money minOrderAmount, Money maxDiscountAmount) {
        validateCouponId(couponId);
        validateUserId(userId);
        this.id = id;
        this.couponId = couponId;
        this.userId = userId;
        this.status = status;
        this.usedOrderId = usedOrderId;
        this.issuedAt = issuedAt;
        this.expiredAt = expiredAt;
        this.usedAt = usedAt;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public static IssuedCoupon create(Coupon coupon, Long userId) {
        if (coupon == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰은 필수입니다.");
        }
        return new IssuedCoupon(null, coupon.getId(), userId, IssuedCouponStatus.AVAILABLE,
                null, ZonedDateTime.now(), coupon.getValidUntil(), null,
                coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getMinOrderAmount(), coupon.getMaxDiscountAmount());
    }

    public static IssuedCoupon of(Long id, Long couponId, Long userId, IssuedCouponStatus status,
                                  ZonedDateTime expiredAt,
                                  DiscountType discountType, Money discountValue,
                                  Money minOrderAmount, Money maxDiscountAmount) {
        return new IssuedCoupon(id, couponId, userId, status, null, ZonedDateTime.now(), expiredAt, null,
                discountType, discountValue, minOrderAmount, maxDiscountAmount);
    }

    public void use(Long orderId) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (this.status != IssuedCouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 가능한 상태의 쿠폰이 아닙니다.");
        }
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        this.status = IssuedCouponStatus.USED;
        this.usedOrderId = orderId;
        this.usedAt = ZonedDateTime.now();
    }

    public void restore() {
        if (this.status != IssuedCouponStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용된 쿠폰만 복원할 수 있습니다.");
        }
        this.status = IssuedCouponStatus.AVAILABLE;
        this.usedOrderId = null;
        this.usedAt = null;
    }

    public void validateUsable(Money orderAmount) {
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (this.minOrderAmount.isGreaterThan(orderAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 않습니다.");
        }
    }

    public Money calculateDiscount(Money orderAmount) {
        Money discount;
        if (this.discountType == DiscountType.FIXED) {
            discount = this.discountValue;
        } else {
            discount = orderAmount.percentage((int) this.discountValue.getAmount().longValue());
            if (this.maxDiscountAmount != null) {
                discount = discount.min(this.maxDiscountAmount);
            }
        }
        return discount.min(orderAmount);
    }

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 쿠폰만 사용할 수 있습니다.");
        }
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(this.expiredAt);
    }

    private void validateCouponId(Long couponId) {
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 필수입니다.");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }
}
