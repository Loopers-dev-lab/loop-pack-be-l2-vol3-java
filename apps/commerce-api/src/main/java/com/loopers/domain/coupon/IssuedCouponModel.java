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
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static lombok.AccessLevel.PROTECTED;

/**
 * 발급 쿠폰(고객이 소유한 인스턴스).
 * 1회 사용 후 USED. 만료일은 발급 시점 템플릿 값 스냅샷(expiredAt).
 */
@Entity
@Table(name = "issued_coupon")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class IssuedCouponModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private IssuedCouponStatus status;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    private IssuedCouponModel(Long userId, Long couponId, IssuedCouponStatus status, ZonedDateTime expiredAt) {
        this.userId = userId;
        this.couponId = couponId;
        this.status = status;
        this.expiredAt = expiredAt;
    }

    /**
     * 발급 쿠폰을 생성한다. 템플릿이 유효(미삭제·미만료)한 경우에만 호출.
     */
    public static IssuedCouponModel issue(Long userId, Long couponId, ZonedDateTime expiredAt) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 null일 수 없습니다.");
        }
        if (couponId == null) {
            throw new IllegalArgumentException("쿠폰 템플릿 ID는 null일 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new IllegalArgumentException("만료일은 null일 수 없습니다.");
        }
        return new IssuedCouponModel(userId, couponId, IssuedCouponStatus.AVAILABLE, expiredAt);
    }

    /**
     * 사용 가능 여부(AVAILABLE이고 미만료).
     */
    public boolean isAvailable(ZonedDateTime now) {
        return status == IssuedCouponStatus.AVAILABLE && now != null && now.isBefore(expiredAt);
    }

    /**
     * 만료 여부(현재 시각 기준).
     */
    public boolean isExpired(ZonedDateTime now) {
        return now != null && !now.isBefore(expiredAt);
    }

    /**
     * 현재 시점 기준 실질 상태를 반환한다.
     * 배치로 EXPIRED를 물리 갱신하지 않으므로, 조회/사용 시점에 동적 판별. (05-transaction-query §1.1)
     */
    public IssuedCouponStatus getActualStatus(ZonedDateTime now) {
        if (this.status == IssuedCouponStatus.USED) {
            return IssuedCouponStatus.USED;
        }
        if (now != null && expiredAt != null && !now.isBefore(expiredAt)) {
            return IssuedCouponStatus.EXPIRED;
        }
        return IssuedCouponStatus.AVAILABLE;
    }

    /**
     * 쿠폰을 사용 처리한다. AVAILABLE·미만료·최소 주문 금액 충족 시에만 호출.
     * 할인 금액을 계산해 CouponDiscount로 반환하고, 상태를 USED로 변경한다.
     *
     * @param orderAmountBeforeDiscount 할인 전 주문 금액
     * @param minOrderAmount            최소 주문 금액 (null이면 조건 없음)
     * @param type                     FIXED 또는 RATE
     * @param value                    정액(원) 또는 정률(%)
     * @param now                       사용 시각
     */
    public CouponDiscount use(BigDecimal orderAmountBeforeDiscount,
                              BigDecimal minOrderAmount,
                              CouponType type,
                              int value,
                              ZonedDateTime now) {
        if (status != IssuedCouponStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용되었거나 사용할 수 없는 쿠폰입니다.");
        }
        if (now != null && !now.isBefore(expiredAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (orderAmountBeforeDiscount == null || orderAmountBeforeDiscount.signum() < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액이 올바르지 않습니다.");
        }
        if (minOrderAmount != null && minOrderAmount.signum() > 0
            && orderAmountBeforeDiscount.compareTo(minOrderAmount) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "최소 주문 금액(" + minOrderAmount + "원) 이상이어야 합니다.");
        }
        BigDecimal discountAmount = computeDiscount(orderAmountBeforeDiscount, type, value);
        BigDecimal afterAmount = orderAmountBeforeDiscount.subtract(discountAmount);
        if (afterAmount.signum() < 0) {
            afterAmount = BigDecimal.ZERO;
            discountAmount = orderAmountBeforeDiscount;
        }
        this.status = IssuedCouponStatus.USED;
        this.usedAt = now != null ? now : ZonedDateTime.now();
        return new CouponDiscount(orderAmountBeforeDiscount, discountAmount, afterAmount);
    }

    private static BigDecimal computeDiscount(BigDecimal orderAmount, CouponType type, int value) {
        if (type == CouponType.FIXED) {
            BigDecimal discount = BigDecimal.valueOf(value);
            return discount.min(orderAmount);
        }
        if (type == CouponType.RATE) {
            return orderAmount.multiply(BigDecimal.valueOf(value)).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.DOWN);
        }
        return BigDecimal.ZERO;
    }
}
