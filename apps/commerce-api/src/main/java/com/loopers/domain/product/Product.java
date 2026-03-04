package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false)
    private Integer likeCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    protected Product() {}

    private Product(Long brandId, String name, String description, Integer price, Integer stockQuantity) {
        validateBrandId(brandId);
        validateName(name);
        validatePrice(price);
        validateStockQuantity(stockQuantity);
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.visibility = Visibility.VISIBLE;
    }

    public static Product create(Long brandId, String name, String description, Integer price, Integer stockQuantity) {
        return new Product(brandId, name, description, price, stockQuantity);
    }

    public void update(String name, String description, Integer price, Integer stockQuantity) {
        validateName(name);
        validatePrice(price);
        validateStockQuantity(stockQuantity);
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public boolean isActive() {
        return this.visibility == Visibility.VISIBLE && getDeletedAt() == null;
    }

    public void changeVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    private void validateBrandId(Long brandId) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수값입니다.");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 이름은 필수값입니다.");
        }
    }

    private void validatePrice(Integer price) {
        if (price == null || price <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0보다 커야 합니다.");
        }
    }

    private void validateStockQuantity(Integer stockQuantity) {
        if (stockQuantity == null || stockQuantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다.");
        }
    }

    public enum Visibility {
        VISIBLE, HIDDEN
    }
}
