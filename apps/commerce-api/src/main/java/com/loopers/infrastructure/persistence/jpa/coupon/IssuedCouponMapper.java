package com.loopers.infrastructure.persistence.jpa.coupon;

import com.loopers.domain.coupon.IssuedCoupon;

/**
 * IssuedCoupon Domain ↔ JPA Entity 변환 Mapper.
 */
public class IssuedCouponMapper {

    private IssuedCouponMapper() {}

    public static IssuedCoupon toDomain(IssuedCouponJpaEntity entity) {
        return IssuedCoupon.reconstitute(
            entity.getId(),
            entity.getUserId(),
            entity.getCouponTemplateId(),
            entity.getStatus(),
            entity.getUsedAt(),
            entity.getIssuedAt(),
            entity.getExpiredAt()
        );
    }

    public static IssuedCouponJpaEntity toJpaEntity(IssuedCoupon domain) {
        return new IssuedCouponJpaEntity(
            domain.getUserId(),
            domain.getCouponTemplateId(),
            domain.getStatus(),
            domain.getExpiredAt()
        );
    }

    public static void updateJpaEntity(IssuedCouponJpaEntity entity, IssuedCoupon domain) {
        entity.setStatus(domain.getStatus());
        entity.setUsedAt(domain.getUsedAt());
    }
}
