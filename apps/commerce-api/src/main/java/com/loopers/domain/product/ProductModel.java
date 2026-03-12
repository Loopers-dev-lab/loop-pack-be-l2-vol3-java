package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class ProductModel extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String description;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "price", nullable = false))
    private Money price;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    protected ProductModel() {}

    public ProductModel(String name, String description, Money price, Long brandId) {
        validateName(name);
        validatePrice(price);
        this.name = name;
        this.description = description;
        this.price = price;
        this.brandId = brandId;
        this.likeCount = 0;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Money getPrice() { return price; }
    public Long getBrandId() { return brandId; }
    public int getLikeCount() { return likeCount; }

    public void update(String name, String description, Money price) {
        validateName(name);
        validatePrice(price);
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 이름은 비어있을 수 없습니다.");
        }
    }

    private void validatePrice(Money price) {
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 필수입니다.");
        }
    }
}
