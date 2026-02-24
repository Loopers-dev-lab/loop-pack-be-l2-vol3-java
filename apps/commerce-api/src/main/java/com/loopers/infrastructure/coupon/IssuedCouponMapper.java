package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.stereotype.Component;

/**
 * IssuedCouponMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class IssuedCouponMapper {

    /**
     * Domain → JPA Entity
     */
    public IssuedCouponEntity toEntity(IssuedCoupon domain) {
        IssuedCouponEntity entity = new IssuedCouponEntity();
        entity.setId(domain.getId());
        entity.setCouponTemplateId(domain.getCouponTemplateId());
        entity.setUserId(domain.getUserId());
        entity.setStatus(domain.getStatus());
        entity.setOrderId(domain.getOrderId());
        entity.setUsedAt(domain.getUsedAt());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setDeletedAt(domain.getDeletedAt());
        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public IssuedCoupon toDomain(IssuedCouponEntity entity) {
        return IssuedCoupon.reconstitute(
                entity.getId(),
                entity.getCouponTemplateId(),
                entity.getUserId(),
                entity.getStatus(),
                entity.getOrderId(),
                entity.getUsedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
