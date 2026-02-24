package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Product extends BaseEntity {

    // 소속 브랜드 ID. 등록 이후 변경 불가 (BR-P02)
    @Column(name = "brand_id", nullable = false, updatable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private Money price;

    @Embedded
    @AttributeOverride(name = "quantity", column = @Column(name = "stock", nullable = false))
    private Stock stock;

    @Column(name = "like_count", nullable = false)
    private int likeCount = 0;

    public Product(Long brandId, String name, Money price, Stock stock) {
        validateBrandId(brandId);
        validateName(name);
        validatePrice(price);
        validateStock(stock);

        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.likeCount = 0;
    }

    public void update(String name, Money price, Stock stock) {
        validateName(name);
        validatePrice(price);
        validateStock(stock);

        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public void decreaseStock(Quantity quantity) {
        this.stock = stock.decrease(quantity);
    }

    public void increaseStock(Quantity quantity) {
        this.stock = stock.increase(quantity);
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (likeCount > 0) {
            this.likeCount--;
        }
    }

    private void validateBrandId(Long brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 비어있을 수 없습니다.");
        }
    }

    private void validatePrice(Money price) {
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
    }

    private void validateStock(Stock stock) {
        if (stock == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 필수입니다.");
        }
    }
}
