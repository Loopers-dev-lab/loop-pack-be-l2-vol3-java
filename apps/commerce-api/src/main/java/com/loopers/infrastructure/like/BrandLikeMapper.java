package com.loopers.infrastructure.like;

import com.loopers.domain.like.BrandLike;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * BrandLikeMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class BrandLikeMapper {

    /**
     * Domain → JPA Entity
     */
    public BrandLikeEntity toEntity(BrandLike domain) {
        BrandLikeEntity entity = new BrandLikeEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setBrandId(domain.getBrandId());
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : ZonedDateTime.now());
        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public BrandLike toDomain(BrandLikeEntity entity) {
        return BrandLike.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getBrandId(),
                entity.getCreatedAt()
        );
    }
}
