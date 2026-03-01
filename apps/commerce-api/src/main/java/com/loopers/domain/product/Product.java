package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.exception.ProductInsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "stock", nullable = false)
    private Integer stock;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Product() {
    }

    private Product(Long brandId, String name, String description, BigDecimal price, Integer stock, Integer likeCount) {
        this.brandId = brandId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.likeCount = likeCount;
    }

    public static Product create(Long brandId, String name, String description, BigDecimal price, Integer stock) {
        return new Product(brandId, name, description, price, stock, 0);
    }

    public void updateInfo(String name, String description, BigDecimal price, Integer stock) {
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
        this.price = validatePrice(price);
        this.stock = validateStock(stock);
    }

    public void increaseStock(Integer quantity) {
        validateQuantity(quantity);
        this.stock += quantity;
    }

    public void decreaseStock(Integer quantity) {
        validateQuantity(quantity);
        if (!hasEnoughStock(quantity)) {
            throw new ProductInsufficientStockException();
        }
        this.stock -= quantity;
    }

    public boolean hasEnoughStock(Integer quantity) {
        validateQuantity(quantity);
        return this.stock >= quantity;
    }

    public void increaseLikeCount() {
        this.likeCount += 1;
    }

    public void decreaseLikeCount() {
        if (this.likeCount <= 0) {
            return;
        }
        this.likeCount -= 1;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }

    @Override
    protected void guard() {
        if (this.brandId == null || this.brandId <= 0) {
            throw new IllegalArgumentException("브랜드 ID는 필수입니다");
        }
        this.name = normalizeName(this.name);
        this.description = normalizeDescription(this.description);
        this.price = validatePrice(this.price);
        this.stock = validateStock(this.stock);
        if (this.likeCount == null || this.likeCount < 0) {
            throw new IllegalArgumentException("좋아요 수는 0 이상이어야 합니다");
        }
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("상품명은 필수입니다");
        }

        String normalized = name.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("상품명은 200자 이하여야 합니다");
        }
        return normalized;
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return "";
        }
        return description.trim();
    }

    private BigDecimal validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("상품 가격은 0 이상이어야 합니다");
        }
        return price;
    }

    private Integer validateStock(Integer stock) {
        if (stock == null || stock < 0) {
            throw new IllegalArgumentException("재고는 0 이상이어야 합니다");
        }
        return stock;
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다");
        }
    }
}
