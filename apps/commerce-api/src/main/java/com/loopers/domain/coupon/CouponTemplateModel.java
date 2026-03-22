package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponTemplateModel extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private CouponType type;

    @Column(name = "value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    @Column(name = "min_order_amount", precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "issued_count", nullable = false)
    private long issuedCount;

    private CouponTemplateModel(String name, CouponType type, BigDecimal value,
                                BigDecimal minOrderAmount, ZonedDateTime expiredAt, Integer totalQuantity) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.totalQuantity = totalQuantity;
        this.issuedCount = 0L;
    }

    public boolean isQuantityLimited() {
        return totalQuantity != null;
    }

    public static CouponTemplateModel create(String name, CouponType type, BigDecimal value,
                                             BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 이름은 필수입니다.");
        }
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 할인 값은 0 이상이어야 합니다.");
        }
        if (type == CouponType.RATE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인율은 100%를 초과할 수 없습니다.");
        }
        if (expiredAt == null || expiredAt.isBefore(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 현재 이후여야 합니다.");
        }
        return new CouponTemplateModel(name, type, value, minOrderAmount, expiredAt, null);
    }

    public static CouponTemplateModel createLimited(String name, CouponType type, BigDecimal value,
                                                     BigDecimal minOrderAmount, ZonedDateTime expiredAt,
                                                     int totalQuantity) {
        if (totalQuantity <= 0) {
            throw new com.loopers.support.error.CoreException(
                    com.loopers.support.error.ErrorType.BAD_REQUEST, "수량 제한은 1 이상이어야 합니다.");
        }
        return new CouponTemplateModel(name, type, value, minOrderAmount, expiredAt, totalQuantity);
    }

    public void update(String name, BigDecimal value, BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 템플릿 이름은 필수입니다.");
        }
        this.name = name;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(this.expiredAt);
    }

    public void markAsDeleted() {
        delete();
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }
}
