package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 도메인 엔티티 (Aggregate Root).
 * 관리자가 생성하는 쿠폰 정의.
 */
public class CouponTemplate {

    private Long id;
    private String name;
    private CouponType type;
    private Money value;
    private Money minOrderAmount;
    private Integer maxDiscountAmount;
    private Integer maxIssueCount;
    private int issuedCount;
    private ZonedDateTime expiredAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime deletedAt;

    private CouponTemplate() {}

    /**
     * 정액 할인 쿠폰 생성.
     *
     * @param name 쿠폰명
     * @param value 할인 금액
     * @param minOrderAmount 최소 주문 금액
     * @param maxIssueCount 최대 발급 수 (null이면 무제한)
     * @param expiredAt 만료일시
     * @return 생성된 쿠폰 템플릿
     */
    public static CouponTemplate createFixed(String name, Money value, Money minOrderAmount,
            Integer maxIssueCount, ZonedDateTime expiredAt) {
        CouponTemplate template = new CouponTemplate();
        template.name = name;
        template.type = CouponType.FIXED;
        template.value = value;
        template.minOrderAmount = minOrderAmount;
        template.maxDiscountAmount = null;
        template.maxIssueCount = maxIssueCount;
        template.issuedCount = 0;
        template.expiredAt = expiredAt;
        template.createdAt = ZonedDateTime.now();
        template.deletedAt = null;
        return template;
    }

    /**
     * 정률 할인 쿠폰 생성.
     *
     * @param name 쿠폰명
     * @param ratePercent 할인율 (1~100)
     * @param minOrderAmount 최소 주문 금액
     * @param maxDiscountAmount 최대 할인 금액 (null이면 무제한)
     * @param maxIssueCount 최대 발급 수 (null이면 무제한)
     * @param expiredAt 만료일시
     * @return 생성된 쿠폰 템플릿
     */
    public static CouponTemplate createRate(String name, int ratePercent, Money minOrderAmount,
            Integer maxDiscountAmount, Integer maxIssueCount, ZonedDateTime expiredAt) {
        CouponTemplate template = new CouponTemplate();
        template.name = name;
        template.type = CouponType.RATE;
        template.value = new Money(ratePercent * 100L); // 퍼센트 * 100
        template.minOrderAmount = minOrderAmount;
        template.maxDiscountAmount = maxDiscountAmount;
        template.maxIssueCount = maxIssueCount;
        template.issuedCount = 0;
        template.expiredAt = expiredAt;
        template.createdAt = ZonedDateTime.now();
        template.deletedAt = null;
        return template;
    }

    /**
     * DB에서 복원 (Infrastructure에서 사용).
     */
    public static CouponTemplate reconstitute(Long id, String name, CouponType type, Money value,
            Money minOrderAmount, Integer maxDiscountAmount, Integer maxIssueCount,
            int issuedCount, ZonedDateTime expiredAt, ZonedDateTime createdAt, ZonedDateTime deletedAt) {
        CouponTemplate template = new CouponTemplate();
        template.id = id;
        template.name = name;
        template.type = type;
        template.value = value;
        template.minOrderAmount = minOrderAmount;
        template.maxDiscountAmount = maxDiscountAmount;
        template.maxIssueCount = maxIssueCount;
        template.issuedCount = issuedCount;
        template.expiredAt = expiredAt;
        template.createdAt = createdAt;
        template.deletedAt = deletedAt;
        return template;
    }

    /**
     * 쿠폰 발급 가능 여부.
     */
    public boolean canIssue() {
        if (maxIssueCount == null) {
            return true;
        }
        return issuedCount < maxIssueCount;
    }

    /**
     * 발급 수 증가.
     *
     * @throws CoreException 발급 불가능한 경우
     */
    public void incrementIssuedCount() {
        if (!canIssue()) {
            throw new CoreException(ErrorType.COUPON_EXHAUSTED);
        }
        issuedCount++;
    }

    /**
     * 할인 금액 계산.
     *
     * @param orderAmount 주문 금액
     * @return 할인 금액
     */
    public Money calculateDiscount(Money orderAmount) {
        if (type == CouponType.FIXED) {
            long discount = Math.min(value.amount(), orderAmount.amount());
            return new Money(discount);
        } else {
            // RATE: value는 퍼센트 * 100 (예: 10% = 1000)
            long discount = orderAmount.amount() * value.amount() / 10000;
            if (maxDiscountAmount != null && discount > maxDiscountAmount) {
                discount = maxDiscountAmount;
            }
            return new Money(discount);
        }
    }

    /**
     * 쿠폰 삭제 (soft delete).
     */
    public void delete() {
        this.deletedAt = ZonedDateTime.now();
    }

    /**
     * 삭제 여부 확인.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public Money getValue() {
        return value;
    }

    public Money getMinOrderAmount() {
        return minOrderAmount;
    }

    public Integer getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public Integer getMaxIssueCount() {
        return maxIssueCount;
    }

    public int getIssuedCount() {
        return issuedCount;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getDeletedAt() {
        return deletedAt;
    }
}
