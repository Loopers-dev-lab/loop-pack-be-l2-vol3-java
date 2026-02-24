package com.loopers.infrastructure.like;

import com.loopers.domain.like.ProductLike;
import org.springframework.stereotype.Component;

/**
 * ProductLikeMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class ProductLikeMapper {

    /**
     * Domain → JPA Entity
     */
    public ProductLikeEntity toEntity(ProductLike domain) {
        ProductLikeEntity entity = new ProductLikeEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setProductId(domain.getProductId());
        entity.setCreatedAt(domain.getCreatedAt());
        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public ProductLike toDomain(ProductLikeEntity entity) {
        return ProductLike.reconstitute(
                entity.getId(),
                entity.getUserId(),
                entity.getProductId(),
                entity.getCreatedAt()
        );
    }
}
