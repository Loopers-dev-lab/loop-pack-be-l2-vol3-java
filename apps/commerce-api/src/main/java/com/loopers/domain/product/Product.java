package com.loopers.domain.product;

import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Product extends BaseEntity {

    @Column(nullable = false)
    private Long brandId;

    @Embedded
    private ProductName name;

    @Embedded
    private ProductThumbnailUrl thumbnailUrl;

    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Embedded
    private Stock stock;

    @Column(nullable = false)
    private long likeCount = 0;

    private String description;

    public static Product create(ProductSpec spec) {
        if (Objects.isNull(spec.brandId())) {
            throw new CoreException(ErrorType.REQUIRED_BRAND_ID);
        }

        Product product = new Product();
        product.brandId = spec.brandId();
        product.name = new ProductName(spec.name());
        product.thumbnailUrl = new ProductThumbnailUrl(spec.thumbnailUrl());
        product.price = Money.wons(spec.price());
        product.stock = Stock.init(spec.stock());
        product.description = spec.description();
        return product;
    }

    public void deductStock(Long quantity) {
        this.stock.deduct(quantity);
    }

    public void update(ModifyProduct product) {
        if (isDeleted()) {
            throw new CoreException(ErrorType.ALREADY_DELETED_PRODUCT);
        }
        this.name = new ProductName(product.name());
        this.thumbnailUrl = new ProductThumbnailUrl(product.thumbnailUrl());
        this.price = Money.wons(product.price());
        this.stock = Stock.init(product.stock());
        this.description = product.description();
    }
}
