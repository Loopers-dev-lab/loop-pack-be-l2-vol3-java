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

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "price", nullable = false)
    private Long price;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "stock_quantity", nullable = false)
    private StockQuantity stockQuantity;

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

    public StockQuantity getStockQuantity() {
        return stockQuantity;
    }
}
