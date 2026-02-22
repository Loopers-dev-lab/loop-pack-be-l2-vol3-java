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
    @AttributeOverride(name = "amount", column = @Column(name = "base_price", nullable = false))
    private MoneyEmbeddable basePrice;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    private ProductJpaEntity(Long brandId, String name, MoneyEmbeddable basePrice, boolean deleted) {
        this.brandId = brandId;
        this.name = name;
        this.basePrice = basePrice;
        this.deleted = deleted;
    }

    public static ProductJpaEntity from(Product product) {
        return new ProductJpaEntity(
                product.getBrandId(),
                product.getName(),
                MoneyEmbeddable.from(product.getBasePrice()),
                product.isDeleted()
        );
    }

    public Product toDomain() {
        return Product.of(getId(), brandId, name, basePrice.toDomain(), deleted);
    }

    public void update(Product product) {
        this.name = product.getName();
        this.basePrice = MoneyEmbeddable.from(product.getBasePrice());
        this.deleted = product.isDeleted();
    }
}
