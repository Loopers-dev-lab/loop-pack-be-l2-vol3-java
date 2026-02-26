package com.loopers.infrastructure.product;

import com.loopers.domain.common.vo.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductStatus;
import org.springframework.stereotype.Component;

/**
 * ProductMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class ProductMapper {

    /**
     * Domain → JPA Entity
     */
    public ProductEntity toEntity(Product product) {
        ProductEntity entity = new ProductEntity();
        entity.setId(product.getId());
        entity.setBrandId(product.getBrandId());
        entity.setName(product.getName());
        entity.setDescription(product.getDescription());
        entity.setBasePrice(product.getBasePrice()); // Money → Integer
        entity.setStatus(product.getStatus());
        entity.setLikeCount(product.getLikeCount());
        entity.setCreatedAt(product.getCreatedAt());
        entity.setUpdatedAt(product.getUpdatedAt());
        entity.setDeletedAt(product.getDeletedAt());
        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public Product toDomain(ProductEntity entity) {
        return Product.reconstitute(
            entity.getId(),
            entity.getBrandId(),
            entity.getName(),
            entity.getDescription(),
            new Money(entity.getBasePrice()), // Integer → Money
            entity.getStatus(),
            entity.getLikeCount(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt()
        );
    }
}
