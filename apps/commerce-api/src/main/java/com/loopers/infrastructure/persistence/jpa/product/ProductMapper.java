package com.loopers.infrastructure.persistence.jpa.product;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;

/**
 * Product 도메인 객체와 JPA 엔티티 간 변환을 담당.
 */
public class ProductMapper {

    private ProductMapper() {}

    /**
     * JPA 엔티티를 도메인 객체로 변환.
     */
    public static Product toDomain(ProductJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Product.reconstitute(
            entity.getId(),
            entity.getBrandId(),
            entity.getName(),
            entity.getDescription(),
            new Money(entity.getPrice()),
            new Stock(entity.getStock()),
            entity.getImageUrl(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getDeletedAt()
        );
    }

    /**
     * 도메인 객체를 새 JPA 엔티티로 변환 (신규 저장용).
     */
    public static ProductJpaEntity toJpaEntity(Product domain) {
        if (domain == null) {
            return null;
        }
        return new ProductJpaEntity(
            domain.getBrandId(),
            domain.getName(),
            domain.getDescription(),
            domain.getPrice().amount(),
            domain.getStock().quantity(),
            domain.getImageUrl()
        );
    }

    /**
     * 기존 JPA 엔티티를 도메인 객체로 업데이트.
     */
    public static void updateJpaEntity(ProductJpaEntity entity, Product domain) {
        entity.setBrandId(domain.getBrandId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setPrice(domain.getPrice().amount());
        entity.setStock(domain.getStock().quantity());
        entity.setImageUrl(domain.getImageUrl());
        if (domain.isDeleted() && entity.getDeletedAt() == null) {
            entity.delete();
        }
    }
}
