package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * CouponTemplateMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class CouponTemplateMapper {

    /**
     * Domain → JPA Entity
     */
    public CouponTemplateEntity toEntity(CouponTemplate domain) {
        CouponTemplateEntity entity = new CouponTemplateEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setDiscountType(domain.getDiscountType());
        entity.setDiscountValue(domain.getDiscountValue());
        entity.setMaxDiscountAmount(domain.getMaxDiscountAmount());
        entity.setMinOrderAmount(domain.getMinOrderAmount());
        entity.setMaxIssueCount(domain.getMaxIssueCount());
        entity.setMaxIssueCountPerUser(domain.getMaxIssueCountPerUser());
        entity.setValidFrom(domain.getValidFrom());
        entity.setValidTo(domain.getValidTo());
        entity.setStatus(domain.getStatus());
        ZonedDateTime now = ZonedDateTime.now();
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : now);
        entity.setUpdatedAt(now);
        entity.setDeletedAt(domain.getDeletedAt());
        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public CouponTemplate toDomain(CouponTemplateEntity entity) {
        return CouponTemplate.reconstitute(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getDiscountType(),
                entity.getDiscountValue(),
                entity.getMaxDiscountAmount(),
                entity.getMinOrderAmount(),
                entity.getMaxIssueCount(),
                entity.getMaxIssueCountPerUser(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
