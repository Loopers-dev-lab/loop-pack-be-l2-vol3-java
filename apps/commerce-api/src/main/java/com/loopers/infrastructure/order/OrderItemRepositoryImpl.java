package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderItemRepositoryImpl implements OrderItemRepository {

    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        List<OrderItemEntity> entities = orderItems.stream()
            .map(OrderItemEntity::create)
            .toList();

        return orderItemJpaRepository.saveAll(entities).stream()
            .map(OrderItemEntity::toDomain)
            .toList();
    }
}
