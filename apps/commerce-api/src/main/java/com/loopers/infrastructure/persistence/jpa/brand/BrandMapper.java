package com.loopers.infrastructure.persistence.jpa.brand;

import com.loopers.domain.brand.Brand;

/**
 * Brand 도메인 객체와 JPA 엔티티 간 변환을 담당.
 */
public class BrandMapper {

    private BrandMapper() {}

    /**
     * JPA 엔티티를 도메인 객체로 변환.
     */
    public static Brand toDomain(BrandJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Brand.reconstitute(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getLogoUrl(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt()
        );
    }

    /**
     * 도메인 객체를 새 JPA 엔티티로 변환 (신규 저장용).
     */
    public static BrandJpaEntity toJpaEntity(Brand domain) {
        if (domain == null) {
            return null;
        }
        return new BrandJpaEntity(
            domain.getName(),
            domain.getDescription(),
            domain.getLogoUrl()
        );
    }

    /**
     * 기존 JPA 엔티티를 도메인 객체로 업데이트.
     */
    public static void updateJpaEntity(BrandJpaEntity entity, Brand domain) {
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setLogoUrl(domain.getLogoUrl());
        if (domain.isDeleted() && entity.getDeletedAt() == null) {
            entity.delete();
        }
    }
}