package com.loopers.domain.stock;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Stock;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "product_stocks")
public class ProductStock extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected ProductStock() {}

    public ProductStock(Long productId, Stock stock) {
        Objects.requireNonNull(productId, "상품 ID는 필수입니다.");
        Objects.requireNonNull(stock, "재고는 필수입니다.");
        this.productId = productId;
        this.quantity = stock.quantity();
    }

    public void deduct(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 수량은 0보다 커야 합니다.");
        }
        Stock current = getStock();
        Stock deducted = current.deduct(quantity);
        this.quantity = deducted.quantity();
    }

    public void restore(int quantity) {
        if (quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "복원 수량은 0보다 커야 합니다.");
        }
        this.quantity += quantity;
        new Stock(this.quantity);
    }

    public void changeQuantity(Stock stock) {
        Objects.requireNonNull(stock, "재고는 필수입니다.");
        this.quantity = stock.quantity();
    }

    public Long getProductId() { return productId; }
    public Stock getStock() { return new Stock(quantity); }
}
