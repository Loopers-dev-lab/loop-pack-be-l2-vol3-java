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

    private CouponTemplateEntity(String name, DiscountType type, Integer discountValue, Integer minOrderAmount, ZonedDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static CouponTemplateEntity create(CouponTemplate template) {
        return new CouponTemplateEntity(
            template.getName().value(),
            template.getType(),
            template.getDiscountValue(),
            template.getMinOrderAmount().value(),
            template.getExpiredAt()
        );
    }

    public CouponTemplate toDomain() {
        return CouponTemplate.restore(this.getId(), this.name, this.type, this.discountValue, this.minOrderAmount, this.expiredAt);
    }

    public void updateFrom(CouponTemplate template) {
        this.name = template.getName().value();
        this.type = template.getType();
        this.discountValue = template.getDiscountValue();
        this.minOrderAmount = template.getMinOrderAmount().value();
    }
}
