package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
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
 * 쿠폰 템플릿(어드민 등록).
 * 정액(FIXED)/정률(RATE), value, minOrderAmount(선택), expiredAt.
 */
@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class CouponTemplateModel extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private int value;

    @Column(name = "min_order_amount", precision = 19, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    private CouponTemplateModel(String name, CouponType type, int value,
                                 BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static CouponTemplateModel create(String name, CouponType type, int value,
                                            BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("쿠폰 이름은 null이거나 비어 있을 수 없습니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("쿠폰 타입은 null일 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new IllegalArgumentException("만료일은 null일 수 없습니다.");
        }
        if (type == CouponType.RATE && (value < 0 || value > 100)) {
            throw new IllegalArgumentException("정률 쿠폰의 value는 0~100 사이여야 합니다.");
        }
        if (type == CouponType.FIXED && value < 0) {
            throw new IllegalArgumentException("정액 쿠폰의 value는 0 이상이어야 합니다.");
        }
        return new CouponTemplateModel(name.trim(), type, value, minOrderAmount, expiredAt);
    }

    public boolean isExpired(ZonedDateTime now) {
        return now != null && !now.isBefore(expiredAt);
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    public void update(String name, CouponType type, int value,
                      BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("쿠폰 이름은 null이거나 비어 있을 수 없습니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("쿠폰 타입은 null일 수 없습니다.");
        }
        if (expiredAt == null) {
            throw new IllegalArgumentException("만료일은 null일 수 없습니다.");
        }
        if (type == CouponType.RATE && (value < 0 || value > 100)) {
            throw new IllegalArgumentException("정률 쿠폰의 value는 0~100 사이여야 합니다.");
        }
        if (type == CouponType.FIXED && value < 0) {
            throw new IllegalArgumentException("정액 쿠폰의 value는 0 이상이어야 합니다.");
        }
        this.name = name.trim();
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }
}
