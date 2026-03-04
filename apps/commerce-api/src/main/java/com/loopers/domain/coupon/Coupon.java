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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "coupons")
@Getter
public class Coupon extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int MAX_RATE_VALUE = 100;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    @Column(nullable = false)
    private int value;

    @Column(name = "min_order_amount")
    private BigDecimal minOrderAmount;

    @Column(name = "max_issue_count", nullable = false)
    private int maxIssueCount;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    protected Coupon() {}

    private Coupon(String name, CouponType type, int value, BigDecimal minOrderAmount,
                   int maxIssueCount, LocalDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.maxIssueCount = maxIssueCount;
        this.issuedCount = 0;
        this.expiredAt = expiredAt;
    }

    public static Coupon create(String name, CouponType type, int value, BigDecimal minOrderAmount,
                                int maxIssueCount, LocalDateTime expiredAt) {
        validateName(name);
        validateValue(type, value);
        validateExpiredAt(expiredAt);
        return new Coupon(name, type, value, minOrderAmount, maxIssueCount, expiredAt);
    }

    public void updateInfo(String name, Integer value, BigDecimal minOrderAmount,
                           Integer maxIssueCount, LocalDateTime expiredAt) {
        validateNotDeleted();
        if (name != null) {
            validateName(name);
            this.name = name;
        }
        if (value != null) {
            validateValue(this.type, value);
            this.value = value;
        }
        if (minOrderAmount != null) {
            this.minOrderAmount = minOrderAmount;
        }
        if (maxIssueCount != null) {
            if (maxIssueCount < this.issuedCount) {
                throw new CoreException(ErrorType.BAD_REQUEST, "현재 발급 수량보다 작게 설정할 수 없습니다");
            }
            this.maxIssueCount = maxIssueCount;
        }
        if (expiredAt != null) {
            validateExpiredAt(expiredAt);
            this.expiredAt = expiredAt;
        }
    }

    public void issue() {
        if (isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다");
        }
        if (issuedCount >= maxIssueCount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 가능 수량이 모두 소진되었습니다");
        }
        this.issuedCount++;
    }

    public boolean isExpired() {
        return expiredAt.isBefore(LocalDateTime.now());
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    public BigDecimal calculateDiscount(BigDecimal totalAmount) {
        return switch (type) {
            case FIXED -> totalAmount.min(BigDecimal.valueOf(value));
            case RATE -> totalAmount.multiply(BigDecimal.valueOf(value))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
        };
    }

    public void validateNotDeleted() {
        if (isDeleted()) {
            throw new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 쿠폰입니다");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 필수입니다");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰명은 100자 이하여야 합니다");
        }
    }

    private static void validateValue(CouponType type, int value) {
        if (type == CouponType.RATE && value > MAX_RATE_VALUE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 할인값입니다");
        }
    }

    private static void validateExpiredAt(LocalDateTime expiredAt) {
        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 현재 이후여야 합니다");
        }
    }
}
