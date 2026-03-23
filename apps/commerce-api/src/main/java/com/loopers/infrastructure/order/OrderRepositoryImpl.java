package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            OrderEntity orderEntity = OrderEntity.create(order);
            OrderEntity savedOrderEntity = orderJpaRepository.save(orderEntity);
            return savedOrderEntity.toDomain();
        }

        OrderEntity orderEntity = orderJpaRepository.findById(order.getId())
            .orElseThrow();
        orderEntity.updateStatus(order.getStatus());
        return orderEntity.toDomain();
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findByIdWithDetails(id)
            .map(OrderEntity::toDomain);
    }
}
