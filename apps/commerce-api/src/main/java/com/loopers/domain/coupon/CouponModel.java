package com.loopers.domain.coupon;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.enums.DiscountType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 JPA 엔티티.
 * <p>
 * 고정 금액(FIXED) 또는 정률(RATE) 할인을 제공하는 쿠폰 템플릿이다.
 * {@link BaseStringIdEntity}를 상속하여 소프트 삭제를 지원한다.
 * </p>
 */
@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponModel extends BaseStringIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long couponId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private DiscountType discountType;

    @Column(name = "value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    private CouponModel(String name, DiscountType discountType, BigDecimal discountValue,
                        BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    /**
     * 쿠폰 템플릿을 생성한다.
     */
    public static CouponModel create(String name, DiscountType discountType, BigDecimal discountValue,
                                      BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        return new CouponModel(name, discountType, discountValue, minOrderAmount, expiredAt);
    }

    /**
     * 할인 금액을 계산한다.
     * <ul>
     *   <li>FIXED: discountValue (주문 금액 초과 불가)</li>
     *   <li>RATE: totalAmount * discountValue / 100, FLOOR 처리</li>
     * </ul>
     */
    public BigDecimal calculateDiscount(BigDecimal totalAmount) {
        if (discountType == DiscountType.FIXED) {
            return discountValue.compareTo(totalAmount) > 0 ? totalAmount : discountValue;
        }
        // RATE: 내림(FLOOR) 처리
        return totalAmount.multiply(discountValue)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
    }

    /**
     * 쿠폰 적용 가능 여부를 검증한다.
     *
     * @param totalAmount 주문 총액 (null이면 minOrderAmount 검증 생략)
     * @throws CoreException 삭제/만료 또는 최소 주문 금액 미충족 시 COUPON_NOT_APPLICABLE
     */
    public void validateApplicable(BigDecimal totalAmount) {
        if (isDeleted() || LocalDateTime.now().isAfter(expiredAt)) {
            throw new CoreException(ErrorType.COUPON_NOT_APPLICABLE);
        }
        if (totalAmount != null && minOrderAmount != null && totalAmount.compareTo(minOrderAmount) < 0) {
            throw new CoreException(ErrorType.COUPON_NOT_APPLICABLE);
        }
    }

    /**
     * 쿠폰 정보를 수정한다.
     */
    public void updateInfo(String name, DiscountType discountType, BigDecimal discountValue,
                            BigDecimal minOrderAmount, LocalDateTime expiredAt) {
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    @Override
    protected void guard() {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 필수입니다.");
        }
        if (discountValue == null || discountValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인값은 0보다 커야 합니다.");
        }
        if (discountType == DiscountType.RATE && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인값은 100 이하여야 합니다.");
        }
    }
}
