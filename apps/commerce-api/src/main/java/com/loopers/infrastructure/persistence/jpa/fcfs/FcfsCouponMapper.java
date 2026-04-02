package com.loopers.infrastructure.persistence.jpa.fcfs;

import com.loopers.domain.fcfs.FcfsCoupon;

/**
 * FcfsCoupon 도메인 객체와 JPA 엔티티 간 변환.
 */
public class FcfsCouponMapper {

    private FcfsCouponMapper() {}

    public static FcfsCoupon toDomain(FcfsCouponJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return FcfsCoupon.reconstitute(
            entity.getId(),
            entity.getName(),
            entity.getTotalQuantity(),
            entity.getIssuedCount(),
            entity.getCreatedAt()
        );
    }

    public static FcfsCouponJpaEntity toJpaEntity(FcfsCoupon domain) {
        if (domain == null) {
            return null;
        }
        return new FcfsCouponJpaEntity(
            domain.getName(),
            domain.getTotalQuantity(),
            domain.getIssuedCount()
        );
    }
}
