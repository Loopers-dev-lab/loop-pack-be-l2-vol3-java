package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.Money;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_templates")
public class CouponTemplate extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    @Column(name = "max_discount_amount")
    private Integer maxDiscountAmount;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "min_order_amount", nullable = false))
    private Money minOrderAmount;

    @Column(name = "max_issue_count", nullable = false)
    private int maxIssueCount;

    @Column(name = "max_issue_count_per_user", nullable = false)
    private int maxIssueCountPerUser;

    @Column(name = "valid_from", nullable = false)
    private ZonedDateTime validFrom;

    @Column(name = "valid_to", nullable = false)
    private ZonedDateTime validTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponTemplateStatus status;

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

    public void update(String name, String description, DiscountType discountType, int discountValue,
                       Integer maxDiscountAmount, int minOrderAmount) {
        this.name = name;
        this.description = description;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minOrderAmount = new Money(minOrderAmount);
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
}
