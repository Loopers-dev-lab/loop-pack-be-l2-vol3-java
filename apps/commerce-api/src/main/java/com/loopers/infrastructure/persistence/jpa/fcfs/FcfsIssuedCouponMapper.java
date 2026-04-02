package com.loopers.infrastructure.persistence.jpa.fcfs;

import com.loopers.domain.fcfs.FcfsIssuedCoupon;

/**
 * FcfsIssuedCoupon 도메인 객체와 JPA 엔티티 간 변환.
 */
public class FcfsIssuedCouponMapper {

    private FcfsIssuedCouponMapper() {}

    public static FcfsIssuedCoupon toDomain(FcfsIssuedCouponJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return FcfsIssuedCoupon.reconstitute(
            entity.getId(),
            entity.getCouponId(),
            entity.getUserId(),
            entity.getIssuedAt()
        );
    }

    public static FcfsIssuedCouponJpaEntity toJpaEntity(FcfsIssuedCoupon domain) {
        if (domain == null) {
            return null;
        }
        return new FcfsIssuedCouponJpaEntity(
            domain.getCouponId(),
            domain.getUserId()
        );
    }
}
