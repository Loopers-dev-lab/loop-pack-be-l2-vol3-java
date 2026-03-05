package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_templates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class CouponTemplate extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CouponType type;

    // 정액: 할인 금액(원), 정률: 할인 비율(%)
    @Column(nullable = false)
    private int value;

    // 쿠폰 적용 최소 주문 금액. null이면 조건 없음 (BR-O11)
    @Column(name = "min_order_amount")
    private Integer minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    public CouponTemplate(String name, CouponType type, int value, Integer minOrderAmount, LocalDateTime expiredAt) {
        validateName(name);
        validateType(type);
        validateValue(value);
        validateExpiredAt(expiredAt);

        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    // 수정 (US-C06)
    public void update(String name, CouponType type, int value, Integer minOrderAmount, LocalDateTime expiredAt) {
        validateName(name);
        validateType(type);
        validateValue(value);
        validateExpiredAt(expiredAt);

        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    // 할인 금액 계산 (BR-C01)
    // FIXED: 정액 할인 (할인액이 주문액 초과 시 주문액 전액 할인)
    // RATE: 정률 할인
    public int calculateDiscount(int originalAmount) {
        return switch (type) {
            case FIXED -> Math.min(value, originalAmount);
            case RATE -> originalAmount * value / 100;
        };
    }

    // 최소 주문 금액 조건 확인 (BR-O11: 쿠폰 적용 전 금액 기준)
    public boolean isSatisfyMinOrderAmount(int amount) {
        return minOrderAmount == null || amount >= minOrderAmount;
    }

    // 최소 주문 금액 미충족 시 예외 (BR-O11)
    public void validateMinOrderAmount(int amount) {
        if (!isSatisfyMinOrderAmount(amount)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "최소 주문 금액(" + minOrderAmount + "원) 조건을 충족하지 못했습니다.");
        }
    }

    // 만료 여부 확인
    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(expiredAt);
    }

    // 만료 검증 — 만료 시 BAD_REQUEST 예외 (BR-C04)
    public void validateNotExpired(LocalDateTime now) {
        if (isExpired(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
    }

    @Override
    protected void guard() {
        validateName(this.name);
        validateType(this.type);
        validateValue(this.value);
        validateExpiredAt(this.expiredAt);
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿명은 비어있을 수 없습니다.");
        }
    }

    private void validateType(CouponType type) {
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
    }

    private void validateValue(int value) {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }
    }

    private void validateExpiredAt(LocalDateTime expiredAt) {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
    }
}
