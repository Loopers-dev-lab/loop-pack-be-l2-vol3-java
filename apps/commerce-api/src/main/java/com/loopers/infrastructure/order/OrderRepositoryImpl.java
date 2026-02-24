package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            OrderJpaEntity entity = OrderJpaEntity.from(order);
            OrderJpaEntity saved = orderJpaRepository.save(entity);
            return saved.toDomain();
        }

        OrderJpaEntity entity = orderJpaRepository.findById(order.getId())
                .orElseThrow(() -> new IllegalStateException("Order not found: " + order.getId()));
        entity.update(order);
        return entity.toDomain();
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id)
                .map(OrderJpaEntity::toDomain);
    }

    @Override
    public List<Order> findByUserId(Long userId) {
        return orderJpaRepository.findByUserId(userId).stream()
                .map(OrderJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<Order> findAll() {
        return orderJpaRepository.findAll().stream()
                .map(OrderJpaEntity::toDomain)
                .toList();
    }
}
