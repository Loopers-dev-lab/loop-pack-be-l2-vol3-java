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

    /** null이면 전역 발급 상한 없음 */
    @Column(name = "max_issue_count")
    private Integer maxIssueCount;

    @Column(name = "issued_count", nullable = false)
    private int issuedCount;

    private CouponTemplateModel(String name, CouponType type, int value,
                                 BigDecimal minOrderAmount, ZonedDateTime expiredAt,
                                 Integer maxIssueCount, int issuedCount) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.maxIssueCount = maxIssueCount;
        this.issuedCount = issuedCount;
    }

    public static CouponTemplateModel create(String name, CouponType type, int value,
                                            BigDecimal minOrderAmount, ZonedDateTime expiredAt) {
        return create(name, type, value, minOrderAmount, expiredAt, null);
    }

    public static CouponTemplateModel create(String name, CouponType type, int value,
                                            BigDecimal minOrderAmount, ZonedDateTime expiredAt,
                                            Integer maxIssueCount) {
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
        if (maxIssueCount != null && maxIssueCount < 1) {
            throw new IllegalArgumentException("발급 상한은 1 이상이거나 비워야 합니다.");
        }
        return new CouponTemplateModel(name.trim(), type, value, minOrderAmount, expiredAt, maxIssueCount, 0);
    }

    public boolean isExpired(ZonedDateTime now) {
        return now != null && !now.isBefore(expiredAt);
    }

    /**
     * 선착순 전역 상한이 설정되어 있고, 이미 그만큼 발급된 경우.
     */
    public boolean isSoldOut() {
        return maxIssueCount != null && issuedCount >= maxIssueCount;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    public void incrementIssuedCountAfterSuccessfulIssue() {
        this.issuedCount = this.issuedCount + 1;
    }

    public void update(String name, CouponType type, int value,
                      BigDecimal minOrderAmount, ZonedDateTime expiredAt, Integer maxIssueCount) {
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
        if (maxIssueCount != null && maxIssueCount < 1) {
            throw new IllegalArgumentException("발급 상한은 1 이상이거나 비워야 합니다.");
        }
        if (maxIssueCount != null && maxIssueCount < this.issuedCount) {
            throw new IllegalArgumentException("발급 상한은 이미 발급된 수보다 작을 수 없습니다.");
        }
        this.name = name.trim();
        this.type = type;
        this.value = value;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.maxIssueCount = maxIssueCount;
    }
}
