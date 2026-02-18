package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
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
    private Integer stockQuantity;

    protected Product() {}

    public Product(Long brandId, String name, Long price, String description, Integer stockQuantity) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.description = description;
        this.stockQuantity = stockQuantity;
    }

    public void decreaseStock(int quantity) {
        // Red 단계: 로직 미구현
    }

    public StockQuantity getStockQuantity() {
        return new StockQuantity(stockQuantity);
    }
}
