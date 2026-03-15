package com.loopers.infrastructure.product.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.vo.DisplayStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "product", indexes = {
        @Index(name = "idx_product_price", columnList = "price"),
        @Index(name = "idx_product_like_count", columnList = "likeCount"),
        @Index(name = "idx_product_brand_price", columnList = "brandId, price"),
        @Index(name = "idx_product_brand_like", columnList = "brandId, likeCount")
})
@SQLRestriction("deleted_at IS NULL")
public class ProductEntity extends BaseEntity {

    @Column
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DisplayStatus displayStatus;

    @Column(nullable = false)
    private long likeCount;

    protected ProductEntity() {}

    private ProductEntity(Long brandId, String name, int price, int stock, DisplayStatus displayStatus) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.displayStatus = displayStatus;
        this.likeCount = 0;
    }

    public static ProductEntity toEntity(Product product) {
        return new ProductEntity(
                product.getBrandId(),
                product.getName().value(),
                product.getPrice().value(),
                product.getStock().value(),
                product.getDisplayStatus()
        );
    }

    public Product toModel() {
        return Product.reconstruct(
                this.getId(),
                this.brandId,
                this.name,
                this.price,
                this.stock,
                this.displayStatus,
                this.likeCount
        );
    }

    public void update(String name, int price, int stock, DisplayStatus displayStatus) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.displayStatus = displayStatus;
    }
}
