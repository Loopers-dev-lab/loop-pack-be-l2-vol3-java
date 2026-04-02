package com.loopers.infrastructure.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.ZonedDateTime;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "coupons")
@NoArgsConstructor
public class CouponEntity extends BaseEntity {

    @Comment("유저 id (ref)")
    @Column(name = "ref_user_id", nullable = false, updatable = false)
    private Long refUserId;

    @Comment("쿠폰 템플릿 id (ref)")
    @Column(name = "ref_template_id", nullable = false, updatable = false)
    private Long refTemplateId;

    @Comment("쿠폰 이름 (스냅샷)")
    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Comment("할인 타입 (스냅샷)")
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, updatable = false)
    private DiscountType type;

    @Comment("할인 값 (스냅샷)")
    @Column(name = "discount_value", nullable = false, updatable = false)
    private Integer discountValue;

    @Comment("최소 주문 금액 (스냅샷)")
    @Column(name = "min_order_amount", nullable = false, updatable = false)
    private Integer minOrderAmount;

    @Comment("만료 날짜 (스냅샷)")
    @Column(name = "expired_at", nullable = false, updatable = false)
    private ZonedDateTime expiredAt;

    @Comment("사용 상태")
    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false)
    private CouponUsageType usageType;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public CouponEntity(Long refUserId, Long refTemplateId, String name, DiscountType type,
        Integer discountValue, Integer minOrderAmount, ZonedDateTime expiredAt) {
        this.refUserId = refUserId;
        this.refTemplateId = refTemplateId;
        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.usageType = CouponUsageType.AVAILABLE;
    }
}
