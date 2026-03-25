package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "brand_name")
    private String brandName;

    @Column(name = "unit_price")
    private Integer unitPrice;

    @Column(name = "quantity")
    private Integer quantity;

    private OrderItem(Long productId, String productName, String brandName, Integer unitPrice, Integer quantity) {
        this.productId = productId;
        this.productName = productName;
        this.brandName = brandName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItem of(Long productId, String productName, String brandName, Integer unitPrice, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 수량이 올바르지 않습니다.");
        }
        return new OrderItem(productId, productName, brandName, unitPrice, quantity);
    }

    public Integer unitPrice() {
        return unitPrice;
    }

    public long subtotal() {
        return (long) unitPrice * quantity;
    }

    public Integer quantity() {
        return quantity;
    }

    public Long productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public String brandName() {
        return brandName;
    }
}
