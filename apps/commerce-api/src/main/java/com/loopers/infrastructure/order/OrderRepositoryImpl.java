package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;
    private final OrderMapper orderMapper;

    public OrderRepositoryImpl(OrderJpaRepository orderJpaRepository, OrderMapper orderMapper) {
        this.orderJpaRepository = orderJpaRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = orderMapper.toEntity(order);
        OrderEntity saved = orderJpaRepository.save(entity);
        return orderMapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findByIdWithItems(id)
                .map(orderMapper::toDomain);
    }

    @Override
    public List<Order> findAllByUserId(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        return orderJpaRepository.findOrdersByUserIdAndCreatedAtBetween(userId, startAt, endAt)
                .stream()
                .map(orderMapper::toDomainWithoutItems)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findAllByUserIdWithItems(Long userId, ZonedDateTime startAt, ZonedDateTime endAt) {
        List<Long> ids = orderJpaRepository.findOrdersByUserIdAndCreatedAtBetween(userId, startAt, endAt)
                .stream()
                .map(OrderEntity::getId)
                .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return List.of();
        }

        return orderJpaRepository.findAllByIdInWithItems(ids)
                .stream()
                .map(orderMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Order> findAll(int page, int size) {
        return orderJpaRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent()
                .stream()
                .map(orderMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return orderJpaRepository.count();
    }
}
