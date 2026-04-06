package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_products_brand_like", columnList = "brand_id, like_count DESC, id DESC"),
    @Index(name = "idx_products_like", columnList = "like_count DESC, id DESC")
})
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "stock_quantity", nullable = false)
    private StockQuantity stockQuantity;

    @Column(name = "like_count", nullable = false)
    private long likeCount = 0L;

    protected Product() {}

    public Product(Long brandId, String name, Long price, String description, Integer stockQuantity) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.description = description;
        this.stockQuantity = new StockQuantity(stockQuantity);
    }

    public void decreaseStock(int quantity) {
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 1 이상이어야 합니다");
        }
        int current = this.stockQuantity.value();
        if (current < quantity) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다");
        }
        this.stockQuantity = new StockQuantity(current - quantity);
    }

    public void increaseStock(int quantity) {
        if (quantity < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복원 수량은 1 이상이어야 합니다");
        }
        int current = this.stockQuantity.value();
        this.stockQuantity = new StockQuantity(current + quantity);
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public Long getPrice() {
        return price;
    }

    public String getDescription() {
        return description;
    }

    public StockQuantity getStockQuantity() {
        return stockQuantity;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 0 미만이 될 수 없습니다");
        }
        this.likeCount--;
    }

    public long getLikeCount() {
        return likeCount;
    }
}
