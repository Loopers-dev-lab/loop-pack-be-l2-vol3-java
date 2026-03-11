package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

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
        ZonedDateTime now = ZonedDateTime.now();
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : now);
        entity.setUpdatedAt(now);
        entity.setDeletedAt(domain.getDeletedAt());
        entity.setCouponName(domain.getCouponName());
        entity.setDiscountType(domain.getDiscountType());
        entity.setDiscountValue(domain.getDiscountValue());
        entity.setMaxDiscountAmount(domain.getMaxDiscountAmount());
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
                entity.getDeletedAt(),
                entity.getCouponName(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getMaxDiscountAmount()
        );
    }
}
