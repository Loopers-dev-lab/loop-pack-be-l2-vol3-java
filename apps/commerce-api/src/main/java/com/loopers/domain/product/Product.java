package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    protected Product() {}

    public Product(Long brandId, String name, Money price) {
        validate(brandId, name, price);
        this.brandId = brandId;
        this.name = name;
        this.price = price.amount();
        this.likeCount = 0;
    }

    public void changeDetails(String name, Money price) {
        validate(this.brandId, name, price);
        this.name = name;
        this.price = price.amount();
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 미만이 될 수 없습니다.");
        }
        this.likeCount--;
    }

    public Long getBrandId() { return brandId; }
    public String getName() { return name; }
    public Money getPrice() { return new Money(price); }
    public int getLikeCount() { return likeCount; }

    private void validate(Long brandId, String name, Money price) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 이름은 비어있을 수 없습니다.");
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 필수입니다.");
        }
    }
}
