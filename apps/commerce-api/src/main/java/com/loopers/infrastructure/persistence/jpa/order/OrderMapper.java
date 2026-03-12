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

        // 할인 정보가 있는 경우
        if (entity.getOriginalAmount() != null && entity.getCouponDiscount() != null) {
            return Order.reconstituteWithDiscount(
                entity.getId(),
                entity.getUserId(),
                items,
                new Money(entity.getTotalPrice()),
                new Money(entity.getOriginalAmount()),
                new Money(entity.getCouponDiscount()),
                new Money(entity.getPointDiscount() != null ? entity.getPointDiscount() : 0L),
                entity.getCouponId(),
                entity.getStatus(),
                entity.getCreatedAt()
            );
        }

        // 기존 주문 (할인 정보 없음)
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
        OrderJpaEntity entity;

        // 할인 정보가 있는 경우
        if (domain.getOriginalAmount() != null) {
            entity = new OrderJpaEntity(
                domain.getUserId(),
                domain.getTotalPrice().amount(),
                domain.getOriginalAmount().amount(),
                domain.getCouponDiscount() != null ? domain.getCouponDiscount().amount() : 0L,
                domain.getPointDiscount() != null ? domain.getPointDiscount().amount() : 0L,
                domain.getCouponId(),
                domain.getStatus()
            );
        } else {
            entity = new OrderJpaEntity(
                domain.getUserId(),
                domain.getTotalPrice().amount(),
                domain.getStatus()
            );
        }

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
