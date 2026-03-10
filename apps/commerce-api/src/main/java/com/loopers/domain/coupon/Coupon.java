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

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Column(nullable = false)
    private Long discountValue;

    @Column(nullable = false)
    private Long minOrderAmount;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    protected Coupon() {}

    private Coupon(String name, DiscountType discountType, Long discountValue, Long minOrderAmount, LocalDateTime expiresAt) {
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiresAt = expiresAt;
    }

    public static Coupon create(String name, DiscountType discountType, Long discountValue, Long minOrderAmount, LocalDateTime expiresAt) {
        validateName(name);
        validateMinOrderAmount(minOrderAmount);
        validateDiscountValue(discountType, discountValue, minOrderAmount);
        validateExpiresAt(expiresAt);

        return new Coupon(name, discountType, discountValue, minOrderAmount, expiresAt);
    }

    public void update(String name, Long discountValue, Long minOrderAmount, LocalDateTime expiresAt) {
        validateName(name);
        validateMinOrderAmount(minOrderAmount);
        validateDiscountValue(this.discountType, discountValue, minOrderAmount);
        validateExpiresAt(expiresAt);

        this.name = name;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiresAt = expiresAt;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 필수입니다.");
        }
    }

    private static void validateDiscountValue(DiscountType discountType, Long discountValue, Long minOrderAmount) {
        if (discountValue == null || discountValue <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0보다 커야 합니다.");
        }

        if (discountType == DiscountType.RATE && discountValue > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인은 100%를 초과할 수 없습니다.");
        }

        if (discountType == DiscountType.FIXED && minOrderAmount != null && discountValue > minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정액 할인금액은 최소 주문 금액을 초과할 수 없습니다.");
        }
    }

    private static void validateMinOrderAmount(Long minOrderAmount) {
        if (minOrderAmount == null || minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
    }

    private static void validateExpiresAt(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
    }

    public void validateIssuable() {
        if (this.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "삭제된 쿠폰입니다.");
        }

        if (this.expiresAt.isBefore(LocalDateTime.now())) {
            throw new CoreException(ErrorType.COUPON_EXPIRED, "만료된 쿠폰입니다.");
        }
    }

    public void validateApplicable(long orderAmount) {
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.MIN_ORDER_AMOUNT_NOT_MET, "최소 주문 금액 조건을 충족하지 않습니다.");
        }
    }

    public long calculateDiscount(long orderAmount) {
        return switch (discountType) {
            case FIXED -> discountValue;
            case RATE -> orderAmount * discountValue / 100;
        };
    }

    public enum DiscountType {
        FIXED, RATE
    }
}
