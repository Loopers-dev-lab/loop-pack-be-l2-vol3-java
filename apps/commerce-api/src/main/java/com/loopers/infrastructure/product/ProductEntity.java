package com.loopers.infrastructure.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class ProductEntity extends BaseEntity {

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

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    protected ProductEntity() {
    }

    public ProductEntity(
            String name,
            Integer price,
            Integer stock,
            String description,
            Long categoryId,
            Long brandId,
            Integer likeCount
    ) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.description = description;
        this.categoryId = categoryId;
        this.brandId = brandId;
        this.likeCount = likeCount;
    }

    public static ProductEntity from(Product product) {
        return new ProductEntity(
                product.name(),
                product.price(),
                product.stock(),
                product.description(),
                product.categoryId(),
                product.brandId(),
                product.likeCount()
        );
    }

    public Product toDomain() {
        return new Product(
                getId(),
                name,
                price,
                stock,
                description,
                categoryId,
                brandId,
                likeCount,
                getDeletedAt()
        );
    }

    public void updateFrom(Product product) {
        this.name = product.name();
        this.price = product.price();
        this.stock = product.stock();
        this.description = product.description();
        this.likeCount = product.likeCount();
    }
}
