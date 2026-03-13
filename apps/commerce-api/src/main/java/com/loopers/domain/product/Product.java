package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product", indexes = {
    @Index(name = "idx_product_brand_id", columnList = "brand_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Embedded
    private Price price;

    @Embedded
    private Stock stock;

    public Product(Long brandId, String name, Price price, Stock stock) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changePrice(Price price) {
        this.price = price;
    }

    public void changeStock(Stock stock) {
        this.stock = stock;
    }

    public void decreaseStock(int quantity) {
        this.stock = this.stock.decrease(quantity);
    }

    public void increaseStock(int quantity) {
        this.stock = this.stock.increase(quantity);
    }
}
