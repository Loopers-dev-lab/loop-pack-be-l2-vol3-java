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

    // 선착순 발급 수량 제한. null이면 무제한 발급
    @Column(name = "max_issue_count")
    private Integer maxIssueCount;

    // 현재 발급된 수량. Consumer가 순차 처리하며 증가시킨다 (Kafka 파티션 키 = templateId)
    @Column(name = "current_issued_count", nullable = false)
    private int currentIssuedCount = 0;

    public CouponTemplate(String name, CouponType type, int value, Integer minOrderAmount, LocalDateTime expiredAt) {
        this(name, type, value, minOrderAmount, expiredAt, null);
    }

    public CouponTemplate(String name, CouponType type, int value, Integer minOrderAmount,
                           LocalDateTime expiredAt, Integer maxIssueCount) {
        validateName(name);
        validateType(type);
        validateValue(value);
        validateRateUpperBound(type, value);
        validateMinOrderAmountInput(minOrderAmount);
        validateExpiredAt(expiredAt);
        validateMaxIssueCount(maxIssueCount);

        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.maxIssueCount = maxIssueCount;
    }

    // 수정 (US-C06)
    public void update(String name, CouponType type, int value, Integer minOrderAmount, LocalDateTime expiredAt) {
        update(name, type, value, minOrderAmount, expiredAt, this.maxIssueCount);
    }

    public void update(String name, CouponType type, int value, Integer minOrderAmount,
                        LocalDateTime expiredAt, Integer maxIssueCount) {
        validateName(name);
        validateType(type);
        validateValue(value);
        validateRateUpperBound(type, value);
        validateMinOrderAmountInput(minOrderAmount);
        validateExpiredAt(expiredAt);
        validateMaxIssueCount(maxIssueCount);

        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.maxIssueCount = maxIssueCount;
    }

    // 선착순 발급 가능 여부 판단. maxIssueCount가 null이면 무제한
    public boolean canIssue() {
        return maxIssueCount == null || currentIssuedCount < maxIssueCount;
    }

    // 발급 카운트 증가. Consumer가 순차 처리 시 호출
    public void incrementIssuedCount() {
        if (!canIssue()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 수량이 모두 소진되었습니다.");
        }
        this.currentIssuedCount++;
    }

    // 잔여 발급 수량. Redis 보정 스케줄러에서 사용
    public int getRemainingIssueCount() {
        if (maxIssueCount == null) {
            return Integer.MAX_VALUE;
        }
        return Math.max(maxIssueCount - currentIssuedCount, 0);
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
        validateRateUpperBound(this.type, this.value);
        validateMinOrderAmountInput(this.minOrderAmount);
        validateExpiredAt(this.expiredAt);
        validateMaxIssueCount(this.maxIssueCount);
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

    private void validateRateUpperBound(CouponType type, int value) {
        if (type == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인 값은 100%를 초과할 수 없습니다.");
        }
    }

    private void validateMinOrderAmountInput(Integer minOrderAmount) {
        if (minOrderAmount != null && minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다.");
        }
    }

    private void validateExpiredAt(LocalDateTime expiredAt) {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
    }

    private void validateMaxIssueCount(Integer maxIssueCount) {
        if (maxIssueCount != null && maxIssueCount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최대 발급 수량은 1 이상이어야 합니다.");
        }
    }
}
