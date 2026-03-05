package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "likes_count", nullable = false)
    private Integer likesCount = 0;

    private Product(Long brandId, String name, String description, BigDecimal price, Integer stock, String imageUrl) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.imageUrl = imageUrl;
        this.likesCount = 0;
    }

    public static Product create(Long brandId, String name, String description, BigDecimal price, Integer stock, String imageUrl) {
        if (name == null || name.trim().isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 필수입니다.");
        }
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0보다 커야 합니다.");
        }
        if (stock == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 필수입니다.");
        }
        if (stock < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
        }
        return new Product(brandId, name, description, price, stock, imageUrl);
    }

    public void update(String name, String description, BigDecimal price, Integer stock, String imageUrl) {
        if (name != null) {
            if (name.trim().isEmpty()) {
                throw new CoreException(ErrorType.BAD_REQUEST, "상품명은 필수입니다.");
            }
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (price != null) {
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "가격은 0보다 커야 합니다.");
            }
            this.price = price;
        }
        if (stock != null) {
            if (stock < 0) {
                throw new CoreException(ErrorType.BAD_REQUEST, "재고는 0 이상이어야 합니다.");
            }
            this.stock = stock;
        }
        if (imageUrl != null) {
            this.imageUrl = imageUrl;
        }
    }

    public void decreaseStock(Integer quantity) {
        if (this.stock < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    String.format("재고가 부족합니다. (상품명: %s, 요청: %d, 재고: %d)", this.name, quantity, this.stock));
        }
        this.stock -= quantity;
    }

    public void increaseLikes() {
        this.likesCount++;
    }

    public void decreaseLikes() {
        if (this.likesCount > 0) {
            this.likesCount--;
        }
    }

    public boolean isDeleted() {
        return this.getDeletedAt() != null;
    }
}
