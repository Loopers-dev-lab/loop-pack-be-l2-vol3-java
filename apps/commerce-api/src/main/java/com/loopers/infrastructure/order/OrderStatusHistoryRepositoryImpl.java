package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderStatusHistory;
import com.loopers.domain.order.OrderStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderStatusHistoryRepositoryImpl implements OrderStatusHistoryRepository {

    private final OrderStatusHistoryJpaRepository orderStatusHistoryJpaRepository;

    @Override
    public OrderStatusHistory save(OrderStatusHistory history) {
        OrderStatusHistoryEntity entity = OrderStatusHistoryEntity.create(history);
        return orderStatusHistoryJpaRepository.save(entity).toDomain();
    }
}
