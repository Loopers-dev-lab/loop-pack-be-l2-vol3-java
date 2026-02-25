package com.loopers.domain.coupon;

import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import java.time.ZonedDateTime;

/**
 * CouponTemplate Domain POJO (순수 도메인 객체)
 * JPA 의존성 없음
 */
public class CouponTemplate {

    private Long id;
    private String name;
    private String description;
    private DiscountType discountType;
    private int discountValue;
    private Integer maxDiscountAmount;
    private Money minOrderAmount;
    private int maxIssueCount;
    private int maxIssueCountPerUser;
    private ZonedDateTime validFrom;
    private ZonedDateTime validTo;
    private CouponTemplateStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected CouponTemplate() {}

    private CouponTemplate(String name, String description, DiscountType discountType, int discountValue,
                           Integer maxDiscountAmount, int minOrderAmount, int maxIssueCount,
                           int maxIssueCountPerUser, ZonedDateTime validFrom, ZonedDateTime validTo) {
        this.name = name;
        this.description = description;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minOrderAmount = new Money(minOrderAmount);
        this.maxIssueCount = maxIssueCount;
        this.maxIssueCountPerUser = maxIssueCountPerUser;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.status = CouponTemplateStatus.ACTIVE;
    }

    public static CouponTemplate create(String name, String description, DiscountType discountType,
                                         int discountValue, Integer maxDiscountAmount, int minOrderAmount,
                                         int maxIssueCount, int maxIssueCountPerUser,
                                         ZonedDateTime validFrom, ZonedDateTime validTo) {
        return new CouponTemplate(name, description, discountType, discountValue, maxDiscountAmount,
                minOrderAmount, maxIssueCount, maxIssueCountPerUser, validFrom, validTo);
    }

    /**
     * DB에서 읽어온 데이터로 도메인 객체 재구성
     * Infrastructure 계층에서만 호출
     */
    public static CouponTemplate reconstitute(Long id, String name, String description,
                                               DiscountType discountType, int discountValue,
                                               Integer maxDiscountAmount, int minOrderAmount,
                                               int maxIssueCount, int maxIssueCountPerUser,
                                               ZonedDateTime validFrom, ZonedDateTime validTo,
                                               CouponTemplateStatus status,
                                               ZonedDateTime createdAt, ZonedDateTime updatedAt,
                                               ZonedDateTime deletedAt) {
        CouponTemplate template = new CouponTemplate();
        template.id = id;
        template.name = name;
        template.description = description;
        template.discountType = discountType;
        template.discountValue = discountValue;
        template.maxDiscountAmount = maxDiscountAmount;
        template.minOrderAmount = new Money(minOrderAmount);
        template.maxIssueCount = maxIssueCount;
        template.maxIssueCountPerUser = maxIssueCountPerUser;
        template.validFrom = validFrom;
        template.validTo = validTo;
        template.status = status;
        template.createdAt = createdAt;
        template.updatedAt = updatedAt;
        template.deletedAt = deletedAt;
        return template;
    }

    public int calculateDiscount(int orderAmount) {
        if (this.discountType == DiscountType.FIXED) {
            return this.discountValue;
        }
        int discount = orderAmount * this.discountValue / 100;
        if (this.maxDiscountAmount != null && discount > this.maxDiscountAmount) {
            return this.maxDiscountAmount;
        }
        return discount;
    }

    public boolean isApplicable(int orderAmount, ZonedDateTime now) {
        if (orderAmount < this.minOrderAmount.toInt()) {
            return false;
        }
        if (now.isBefore(this.validFrom) || now.isAfter(this.validTo)) {
            return false;
        }
        return true;
    }

    public void changeStatus(CouponTemplateStatus status) {
        this.status = status;
    }

    /**
     * 쿠폰 템플릿 부분 수정 (null이면 기존값 유지, 빈값이면 검증 에러)
     *
     * maxIssueCount, maxIssueCountPerUser, validFrom, validTo는 발급 계약 조건이므로
     * 이미 발급된 쿠폰과의 정합성을 위해 수정 불가. status는 changeStatus()로 별도 관리.
     */
    public void update(String name, String description, DiscountType discountType, Integer discountValue,
                       Integer maxDiscountAmount, Integer minOrderAmount) {
        if (name != null) {
            if (name.isBlank()) {
                throw new CoreException(CouponErrorType.INVALID_TEMPLATE_NAME);
            }
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (discountType != null) {
            this.discountType = discountType;
        }
        if (discountValue != null) {
            this.discountValue = discountValue;
        }
        if (maxDiscountAmount != null) {
            this.maxDiscountAmount = maxDiscountAmount;
        }
        if (minOrderAmount != null) {
            this.minOrderAmount = new Money(minOrderAmount);
        }
    }

    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public DiscountType getDiscountType() {
        return this.discountType;
    }

    public int getDiscountValue() {
        return this.discountValue;
    }

    public Integer getMaxDiscountAmount() {
        return this.maxDiscountAmount;
    }

    public int getMinOrderAmount() {
        return this.minOrderAmount.toInt();
    }

    public int getMaxIssueCount() {
        return this.maxIssueCount;
    }

    public int getMaxIssueCountPerUser() {
        return this.maxIssueCountPerUser;
    }

    public ZonedDateTime getValidFrom() {
        return this.validFrom;
    }

    public ZonedDateTime getValidTo() {
        return this.validTo;
    }

    public CouponTemplateStatus getStatus() {
        return this.status;
    }

    public Long getId() {
        return this.id;
    }

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return this.deletedAt;
    }
}
