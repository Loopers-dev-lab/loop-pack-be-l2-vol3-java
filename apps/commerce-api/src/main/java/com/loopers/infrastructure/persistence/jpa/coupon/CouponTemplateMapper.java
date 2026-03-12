package com.loopers.infrastructure.persistence.jpa.coupon;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponTemplate;

/**
 * CouponTemplate Domain ↔ JPA Entity 변환 Mapper.
 */
public class CouponTemplateMapper {

    private CouponTemplateMapper() {}

    public static CouponTemplate toDomain(CouponTemplateJpaEntity entity) {
        return CouponTemplate.reconstitute(
            entity.getId(),
            entity.getName(),
            entity.getType(),
            new Money(entity.getValue()),
            new Money(entity.getMinOrderAmount()),
            entity.getMaxDiscountAmount(),
            entity.getMaxIssueCount(),
            entity.getIssuedCount(),
            entity.getExpiredAt(),
            entity.getCreatedAt(),
            entity.getDeletedAt()
        );
    }

    public static CouponTemplateJpaEntity toJpaEntity(CouponTemplate domain) {
        return new CouponTemplateJpaEntity(
            domain.getName(),
            domain.getType(),
            domain.getValue().amount(),
            domain.getMinOrderAmount().amount(),
            domain.getMaxDiscountAmount(),
            domain.getMaxIssueCount(),
            domain.getIssuedCount(),
            domain.getExpiredAt()
        );
    }

    public static void updateJpaEntity(CouponTemplateJpaEntity entity, CouponTemplate domain) {
        entity.setName(domain.getName());
        entity.setType(domain.getType());
        entity.setValue(domain.getValue().amount());
        entity.setMinOrderAmount(domain.getMinOrderAmount().amount());
        entity.setMaxDiscountAmount(domain.getMaxDiscountAmount());
        entity.setMaxIssueCount(domain.getMaxIssueCount());
        entity.setIssuedCount(domain.getIssuedCount());
        entity.setExpiredAt(domain.getExpiredAt());
        if (domain.isDeleted()) {
            entity.delete();
        }
    }
}
