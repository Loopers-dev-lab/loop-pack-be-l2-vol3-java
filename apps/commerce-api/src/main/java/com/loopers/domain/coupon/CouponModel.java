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
import org.springframework.util.StringUtils;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponModel extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private Long value;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    public CouponModel(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, minOrderAmount, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public void update(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        validate(name, type, value, minOrderAmount, expiredAt);
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public boolean isExpired() {
        return expiredAt.isBefore(ZonedDateTime.now()) || expiredAt.isEqual(ZonedDateTime.now());
    }

    public long calculateDiscount(long orderAmount) {
        if (orderAmount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 1원 이상이어야 합니다.");
        }
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (minOrderAmount != null && orderAmount < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액 조건을 충족하지 못했습니다.");
        }

        long discount = switch (type) {
            case FIXED -> value;
            case RATE -> (orderAmount * value) / 100;
        };

        return Math.min(discount, orderAmount);
    }

    private void validate(String name, CouponType type, Long value, Long minOrderAmount, ZonedDateTime expiredAt) {
        if (!StringUtils.hasText(name)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 필수입니다.");
        }
        if (type == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 타입은 필수입니다.");
        }
        if (value == null || value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 값은 1 이상이어야 합니다.");
        }
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 쿠폰 할인율은 100 이하여야 합니다.");
        }
        if (minOrderAmount != null && minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료 시각은 필수입니다.");
        }
    }
}
