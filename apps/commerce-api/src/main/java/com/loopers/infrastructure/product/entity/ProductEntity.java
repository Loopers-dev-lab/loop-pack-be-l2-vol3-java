package com.loopers.infrastructure.product.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.model.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "product")
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
    private String displayStatus;

    protected ProductEntity() {}

    private ProductEntity(Long brandId, String name, int price, int stock, String displayStatus) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.displayStatus = displayStatus;
    }

    public static ProductEntity toEntity(Product product) {
        return new ProductEntity(
                product.getBrandId(),
                product.getName().value(),
                product.getPrice().value(),
                product.getStock().value(),
                product.getDisplayStatus().name()
        );
    }

    public Product toModel() {
        return Product.reconstruct(
                this.getId(),
                this.brandId,
                this.name,
                this.price,
                this.stock,
                this.displayStatus
        );
    }

    public void update(String name, int price, int stock, String displayStatus) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.displayStatus = displayStatus;
    }
}
