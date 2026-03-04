package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price", nullable = false)
    private int price;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Version
    @Column(name = "version")
    private Long version;

    protected Product() {}

    public Product(Long brandId, String name, Money price, Stock stock) {
        validate(brandId, name, price, stock);
        this.brandId = brandId;
        this.name = name;
        this.price = price.amount();
        this.stock = stock.quantity();
        this.likeCount = 0;
    }

    public void changeDetails(String name, Money price, Stock stock) {
        validate(this.brandId, name, price, stock);
        this.name = name;
        this.price = price.amount();
        this.stock = stock.quantity();
    }

    public void deductStock(int quantity) {
        Stock currentStock = getStock();
        Stock deducted = currentStock.deduct(quantity);
        this.stock = deducted.quantity();
    }

    public void restoreStock(int quantity) {
        this.stock += quantity;
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
    public Stock getStock() { return new Stock(stock); }
    public int getLikeCount() { return likeCount; }

    private void validate(Long brandId, String name, Money price, Stock stock) {
        if (brandId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 이름은 비어있을 수 없습니다.");
        }
        if (price == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 가격은 필수입니다.");
        }
        if (stock == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 재고는 필수입니다.");
        }
    }
}
