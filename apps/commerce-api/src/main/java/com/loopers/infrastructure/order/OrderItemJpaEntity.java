package com.loopers.infrastructure.order;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.order.OrderItem;
import com.loopers.infrastructure.common.MoneyEmbeddable;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemJpaEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "option_name", nullable = false)
    private String optionName;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "price", nullable = false))
    private MoneyEmbeddable price;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    private OrderItemJpaEntity(OrderJpaEntity order, Long optionId, String productName,
                               String optionName, MoneyEmbeddable price, int quantity) {
        this.order = order;
        this.optionId = optionId;
        this.productName = productName;
        this.optionName = optionName;
        this.price = price;
        this.quantity = quantity;
    }

    public static OrderItemJpaEntity of(OrderJpaEntity order, OrderItem orderItem) {
        return new OrderItemJpaEntity(
                order,
                orderItem.getOptionId(),
                orderItem.getProductName(),
                orderItem.getOptionName(),
                MoneyEmbeddable.from(orderItem.getPrice()),
                orderItem.getQuantity()
        );
    }

    public OrderItem toDomain() {
        return OrderItem.of(optionId, productName, optionName, price.toDomain(), quantity);
    }
}
