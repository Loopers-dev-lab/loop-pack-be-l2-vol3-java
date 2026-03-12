package com.loopers.infrastructure.product;

import com.loopers.domain.AutoIncrementBaseEntity;
import com.loopers.domain.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductEntity extends AutoIncrementBaseEntity {

    @Column(name = "reference_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false, unique = true)
    private UUID referenceId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "description")
    private String description;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "category_reference_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID categoryReferenceId;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "brand_reference_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID brandReferenceId;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    protected ProductEntity() {
    }

    public ProductEntity(
            UUID referenceId,
            String name,
            Integer price,
            Integer stock,
            String description,
            Long categoryId,
            UUID categoryReferenceId,
            Long brandId,
            UUID brandReferenceId,
            Integer likeCount
    ) {
        this.referenceId = referenceId;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.description = description;
        this.categoryId = categoryId;
        this.categoryReferenceId = categoryReferenceId;
        this.brandId = brandId;
        this.brandReferenceId = brandReferenceId;
        this.likeCount = likeCount;
    }

    public static ProductEntity from(Product product, Long categoryId, Long brandId) {
        UUID resolvedReferenceId = product.id() != null ? product.id() : UUID.randomUUID();
        return new ProductEntity(
                resolvedReferenceId,
                product.name(),
                product.price(),
                product.stock(),
                product.description(),
                categoryId,
                product.categoryId(),
                brandId,
                product.brandId(),
                product.likeCount()
        );
    }

    public Product toDomain() {
        return new Product(
                referenceId,
                name,
                price,
                stock,
                description,
                categoryReferenceId,
                brandReferenceId,
                likeCount,
                getDeletedAt()
        );
    }

    public void updateFrom(Product product, Long categoryId) {
        this.name = product.name();
        this.price = product.price();
        this.stock = product.stock();
        this.description = product.description();
        this.categoryId = categoryId;
        this.categoryReferenceId = product.categoryId();
        this.likeCount = product.likeCount();
    }
}
