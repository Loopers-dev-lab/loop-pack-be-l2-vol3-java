package com.loopers.domain.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.vo.OrderItemId;
import com.loopers.infrastructure.jpa.converter.OrderItemIdConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemModel extends BaseEntity {

    @Convert(converter = OrderItemIdConverter.class)
    @Column(name = "order_item_id", nullable = false, unique = true, length = 36)
    private OrderItemId orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderModel order;

    @Column(name = "ref_product_id", nullable = false)
    private Long refProductId; // 스냅샷: 주문 시점의 상품 DB PK

    @Column(name = "product_id", nullable = false, length = 20)
    private String productId; // 스냅샷: 주문 시점의 상품 ID

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName; // 스냅샷: 주문 시점의 상품명

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price; // 스냅샷: 주문 시점의 가격

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private OrderItemModel(Long refProductId, String productId, String productName, BigDecimal price, int quantity) {
        this.orderItemId = OrderItemId.generate();
        this.refProductId = refProductId;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public static OrderItemModel create(Long refProductId, String productId, String productName, BigDecimal price, int quantity) {
        return new OrderItemModel(refProductId, productId, productName, price, quantity);
    }

    public BigDecimal getTotalPrice() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    void setOrder(OrderModel order) {
        this.order = order;
    }
}
