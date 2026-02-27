package com.loopers.infrastructure.order.entity;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.model.OrderProduct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "order_product")
public class OrderProductEntity extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private int productPrice;

    @Column(nullable = false)
    private int quantity;

    protected OrderProductEntity() {}

    private OrderProductEntity(Long orderId, Long productId, String productName, int productPrice, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.productName = productName;
        this.productPrice = productPrice;
        this.quantity = quantity;
    }

    public static OrderProductEntity toEntity(OrderProduct orderProduct, Long orderId) {
        return new OrderProductEntity(
                orderId,
                orderProduct.getProductId(),
                orderProduct.getProductName().value(),
                orderProduct.getPrice().value(),
                orderProduct.getQuantity().value()
        );
    }

    public OrderProduct toModel() {
        return OrderProduct.reconstruct(
                this.getId(),
                this.productId,
                this.productName,
                this.productPrice,
                this.quantity
        );
    }
}
