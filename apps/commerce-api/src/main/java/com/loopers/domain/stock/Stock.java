package com.loopers.domain.stock;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Table(name = "stocks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stocks_product_id", columnNames = "product_id")
})
@Getter
public class Stock extends BaseEntity {

    private static final int QUANTITY_MAX = 9_999_999;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "confirmed_quantity", nullable = false)
    private int confirmedQuantity;

    protected Stock() {
    }

    private Stock(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
        this.reservedQuantity = 0;
        this.confirmedQuantity = 0;
    }

    public static Stock create(Long productId, int quantity) {
        validateProductId(productId);
        validateQuantity(quantity);
        return new Stock(productId, quantity);
    }

    public int getAvailableQuantity() {
        return this.quantity - this.reservedQuantity - this.confirmedQuantity;
    }

    public static void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 0 이상이어야 합니다");
        }
        if (quantity > QUANTITY_MAX) {
            throw new CoreException(ErrorType.BAD_REQUEST, "재고 수량은 9,999,999 이하여야 합니다");
        }
    }

    private static void validateProductId(Long productId) {
        if (productId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "상품 ID는 필수입니다");
        }
    }
}
