package com.loopers.infrastructure.persistence.jpa.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;

import java.util.List;

/**
 * Order Domain ↔ JPA Entity 변환 Mapper.
 */
public class OrderMapper {

    private OrderMapper() {}

    public static Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
            .map(OrderMapper::toOrderItemDomain)
            .toList();

        return Order.reconstitute(
            entity.getId(),
            entity.getUserId(),
            items,
            new Money(entity.getTotalPrice()),
            entity.getStatus(),
            entity.getCreatedAt()
        );
    }

    public static OrderItem toOrderItemDomain(OrderItemJpaEntity entity) {
        return OrderItem.reconstitute(
            entity.getId(),
            entity.getProductId(),
            entity.getProductName(),
            entity.getQuantity(),
            new Money(entity.getPriceSnapshot())
        );
    }

    public static OrderJpaEntity toJpaEntity(Order domain) {
        OrderJpaEntity entity = new OrderJpaEntity(
            domain.getUserId(),
            domain.getTotalPrice().amount(),
            domain.getStatus()
        );

        for (OrderItem item : domain.getItems()) {
            OrderItemJpaEntity itemEntity = new OrderItemJpaEntity(
                entity,
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getPriceSnapshot().amount()
            );
            entity.addItem(itemEntity);
        }

        return entity;
    }
}
