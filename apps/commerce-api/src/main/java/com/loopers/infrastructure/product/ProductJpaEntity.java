package com.loopers.infrastructure.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.common.MoneyEmbeddable;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductJpaEntity extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private MoneyEmbeddable price;

    @Column(name = "stock", nullable = false)
    private int stock;

    private ProductJpaEntity(Long brandId, String name, MoneyEmbeddable price, int stock) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public static ProductJpaEntity from(Product product) {
        return new ProductJpaEntity(
                product.getBrandId(),
                product.getName(),
                MoneyEmbeddable.from(product.getPrice()),
                product.getStock()
        );
    }

    public Product toDomain() {
        return Product.of(getId(), brandId, name, price.toDomain(), stock);
    }

    public void update(Product product) {
        this.brandId = product.getBrandId();
        this.name = product.getName();
        this.price = MoneyEmbeddable.from(product.getPrice());
        this.stock = product.getStock();
    }
}
