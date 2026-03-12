package com.loopers.domain.stock;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "stock", uniqueConstraints = @UniqueConstraint(columnNames = "product_id"))
public class StockModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    protected StockModel() {}

    public StockModel(Long productId, int quantity) {
        validateQuantity(quantity);
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }

    public void decrease(int amount) {
        if (this.quantity < amount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고가 부족합니다.");
        }
        this.quantity -= amount;
    }

    public void update(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }

    public boolean hasEnough(int amount) {
        return this.quantity >= amount;
    }

    private void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다.");
        }
    }
}
