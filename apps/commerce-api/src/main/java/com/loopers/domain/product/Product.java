package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ProductErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "base_price", nullable = false))
    private Money basePrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    protected Product() {}

    private Product(Long brandId, String name, String description, int basePrice) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.basePrice = new Money(basePrice);
        this.status = ProductStatus.ACTIVE;
        this.likeCount = 0;
    }

    public static Product create(Long brandId, String name, String description, int basePrice) {
        return new Product(brandId, name, description, basePrice);
    }

    public void update(String name, String description, int basePrice, ProductStatus status) {
        this.name = name;
        this.description = description;
        this.basePrice = new Money(basePrice);
        this.status = status;
    }

    @Override
    public void delete() {
        if (getDeletedAt() != null) {
            throw new CoreException(ProductErrorType.ALREADY_DELETED);
        }
        super.delete();
    }

    public boolean isDisplayable() {
        return this.status == ProductStatus.ACTIVE || this.status == ProductStatus.SOLDOUT;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public Long getBrandId() {
        return this.brandId;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public int getBasePrice() {
        return this.basePrice.toInt();
    }

    public ProductStatus getStatus() {
        return this.status;
    }

    public int getLikeCount() {
        return this.likeCount;
    }
}
