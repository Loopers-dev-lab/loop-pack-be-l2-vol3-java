package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        OrderEntity entity;
        if (order.id() != null) {
            entity = orderJpaRepository.findById(order.id())
                    .orElseGet(() -> OrderEntity.from(order));
            entity.cancel();
        } else {
            entity = OrderEntity.from(order);
            for (OrderItem item : order.items()) {
                OrderItemEntity itemEntity = new OrderItemEntity(entity, item);
                entity.addItem(itemEntity);
            }
        }
        return orderJpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return orderJpaRepository.findById(id)
                .filter(e -> e.getDeletedAt() == null)
                .map(OrderEntity::toDomain);
    }

    @Override
    public Page<Order> findByMemberId(String memberId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable) {
        return orderJpaRepository.findByMemberIdAndOrderDateBetween(memberId, startAt, endAt, pageable)
                .map(OrderEntity::toDomain);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return orderJpaRepository.findAllActive(pageable)
                .map(OrderEntity::toDomain);
    }

    @Override
    public boolean existsOrderItemByProductId(UUID productId) {
        return orderJpaRepository.existsByProductId(productId);
    }
}
