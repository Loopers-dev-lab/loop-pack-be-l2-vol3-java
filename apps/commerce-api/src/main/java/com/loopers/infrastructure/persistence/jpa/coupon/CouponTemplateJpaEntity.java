package com.loopers.infrastructure.persistence.jpa.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 JPA 엔티티.
 */
@Entity
@Table(name = "coupon_templates")
public class CouponTemplateJpaEntity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private CouponType type;

    @Column(name = "value", nullable = false)
    private Long value;

    @Column(name = "min_order_amount", nullable = false)
    private Long minOrderAmount;

    @Column(name = "max_discount_amount")
    private Integer maxDiscountAmount;

    @Column(name = "max_issue_count")
    private Integer maxIssueCount;

    @Column(name = "issued_count", nullable = false)
    private Integer issuedCount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    protected CouponTemplateJpaEntity() {}

    public CouponTemplateJpaEntity(String name, CouponType type, Long value,
            Long minOrderAmount, Integer maxDiscountAmount,
            Integer maxIssueCount, int issuedCount, ZonedDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.maxIssueCount = maxIssueCount;
        this.issuedCount = issuedCount;
        this.expiredAt = expiredAt;
    }

    public String getName() {
        return name;
    }

    public CouponType getType() {
        return type;
    }

    public Long getValue() {
        return value;
    }

    public Long getMinOrderAmount() {
        return minOrderAmount;
    }

    public Integer getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public Integer getMaxIssueCount() {
        return maxIssueCount;
    }

    public Integer getIssuedCount() {
        return issuedCount;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setType(CouponType type) {
        this.type = type;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    public void setMinOrderAmount(Long minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }

    public void setMaxDiscountAmount(Integer maxDiscountAmount) {
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public void setMaxIssueCount(Integer maxIssueCount) {
        this.maxIssueCount = maxIssueCount;
    }

    public void setIssuedCount(Integer issuedCount) {
        this.issuedCount = issuedCount;
    }

    public void setExpiredAt(ZonedDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }
}
