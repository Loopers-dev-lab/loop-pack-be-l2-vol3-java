package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.OrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/**
 * OrderItem DB 엔티티
 */
@Entity
@Table(name = "order_item")
@NoArgsConstructor
public class OrderItemEntity extends BaseEntity {

    @Comment("주문 id (ref)")
    @Column(name = "ref_order_id", insertable = false, updatable = false)
    private Long refOrderId;

    @Comment("상품 id (ref)")
    @Column(name = "ref_product_id", nullable = false, updatable = false)
    private Long refProductId;

    @Comment("주문 수량")
    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Comment("주문 금액")
    @Column(name = "price", nullable = false, updatable = false)
    private Integer price;

    private OrderItemEntity(Long refOrderId, Long refProductId, Integer quantity, Integer price) {
        this.refOrderId = refOrderId;
        this.refProductId = refProductId;
        this.quantity = quantity;
        this.price = price;
    }

    public static OrderItemEntity create(OrderItem orderItem) {
        return new OrderItemEntity(
            orderItem.refOrderId(),
            orderItem.refProductId(),
            orderItem.quantity(),
            orderItem.price().value()
        );
    }

    public OrderItem toDomain() {
        return OrderItem.create(
            this.getId(),
            this.refOrderId,
            this.refProductId,
            this.quantity,
            this.price
        );
    }

    public Long getRefOrderId() {
        return refOrderId;
    }

    public Long getRefProductId() {
        return refProductId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Integer getPrice() {
        return price;
    }
}
