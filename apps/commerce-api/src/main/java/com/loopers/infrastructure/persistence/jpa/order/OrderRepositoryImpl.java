package com.loopers.infrastructure.persistence.jpa.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * OrderRepository 구현체 (Adapter).
 * Domain Repository 인터페이스를 JPA로 구현.
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = OrderMapper.toJpaEntity(order);
        OrderJpaEntity saved = jpaRepository.save(entity);
        return OrderMapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findByIdWithItems(id)
            .map(OrderMapper::toDomain);
    }

    @Override
    public Optional<Order> findByIdAndUserId(Long id, Long userId) {
        return jpaRepository.findByIdAndUserIdWithItems(id, userId)
            .map(OrderMapper::toDomain);
    }

    @Override
    public List<Order> findAllByUserId(Long userId, int offset, int limit) {
        int page = offset / limit;
        return jpaRepository.findAllByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, limit))
            .getContent()
            .stream()
            .map(OrderMapper::toDomain)
            .toList();
    }

    @Override
    public long countByUserId(Long userId) {
        return jpaRepository.countByUserId(userId);
    }
}
