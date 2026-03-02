package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandStatus;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * BrandMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class BrandMapper {

    /**
     * Domain → JPA Entity
     */
    public BrandEntity toEntity(Brand brand) {
        BrandEntity entity = new BrandEntity();
        entity.setId(brand.getId());
        entity.setName(brand.getName());
        entity.setDescription(brand.getDescription());
        entity.setStatus(brand.getStatus());
        ZonedDateTime now = ZonedDateTime.now();
        entity.setCreatedAt(brand.getCreatedAt() != null ? brand.getCreatedAt() : now);
        entity.setUpdatedAt(now);
        entity.setDeletedAt(brand.getDeletedAt());
        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public Brand toDomain(BrandEntity entity) {
        return Brand.reconstitute(
            entity.getId(),
            entity.getName(),
            entity.getDescription(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt()
        );
    }
}
