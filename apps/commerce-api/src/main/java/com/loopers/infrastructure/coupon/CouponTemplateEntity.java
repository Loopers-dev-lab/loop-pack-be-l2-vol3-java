package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.DiscountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * 쿠폰 템플릿 DB 엔티티
 */
@Entity
@Table(name = "coupon_templates")
@NoArgsConstructor
public class CouponTemplateEntity extends BaseEntity {

    @Comment("쿠폰 이름")
    @Column(name = "name", nullable = false)
    private String name;

    @Comment("할인 타입")
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType type;

    @Comment("할인 값 (FIXED: 금액, RATE: 할인율%)")
    @Column(name = "discount_value", nullable = false)
    private Integer discountValue;

    @Comment("최소 주문 금액")
    @Column(name = "min_order_amount", nullable = false)
    private Integer minOrderAmount;

    @Comment("만료 날짜")
    @Column(name = "expired_at", nullable = false, updatable = false)
    private ZonedDateTime expiredAt;

    @Comment("최대 발급 수량 (null이면 무제한)")
    @Column(name = "max_issuance_count")
    private Integer maxIssuanceCount;

    @Comment("현재 발급된 수량")
    @Column(name = "issued_count", nullable = false)
    private Integer issuedCount = 0;

    private CouponTemplateEntity(String name, DiscountType type, Integer discountValue, Integer minOrderAmount, ZonedDateTime expiredAt, Integer maxIssuanceCount) {
        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.maxIssuanceCount = maxIssuanceCount;
        this.issuedCount = 0;
    }

    public static CouponTemplateEntity create(CouponTemplate template) {
        return new CouponTemplateEntity(
            template.getName().value(),
            template.getType(),
            template.getDiscountValue(),
            template.getMinOrderAmount().value(),
            template.getExpiredAt(),
            template.getMaxIssuanceCount()
        );
    }

    public CouponTemplate toDomain() {
        return CouponTemplate.restore(this.getId(), this.name, this.type, this.discountValue, this.minOrderAmount, this.expiredAt, this.maxIssuanceCount, this.issuedCount);
    }

    public void updateFrom(CouponTemplate template) {
        this.name = template.getName().value();
        this.type = template.getType();
        this.discountValue = template.getDiscountValue();
        this.minOrderAmount = template.getMinOrderAmount().value();
    }
}
