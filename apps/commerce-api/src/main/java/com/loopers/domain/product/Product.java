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

    public static Product create(Long brandId, String name, String thumbnailUrl, Long price, Long stock, String description) {
        if (Objects.isNull(brandId)) {
            throw new CoreException(ErrorType.REQUIRED_BRAND_ID);
        }

        Product product = new Product();
        product.brandId = brandId;
        product.name = new ProductName(name);
        product.thumbnailUrl = new ProductThumbnailUrl(thumbnailUrl);
        product.price = Money.wons(price);
        product.stock = new Stock(stock);
        product.description = description;
        return product;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        this.likeCount--;
    }

    public void update(String name, String thumbnailUrl, Long price, Long stock, String description) {
        if (isDeleted()) {
            throw new CoreException(ErrorType.ALREADY_DELETED_PRODUCT);
        }
        this.name = new ProductName(name);
        this.thumbnailUrl = new ProductThumbnailUrl(thumbnailUrl);
        this.price = Money.wons(price);
        this.stock = new Stock(stock);
        this.description = description;
    }
}
