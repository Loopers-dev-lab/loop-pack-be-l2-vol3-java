package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    // Command
    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    // Query
    @Override
    public Optional<Order> findByIdWithItems(Long id) {
        return orderJpaRepository.findByIdWithItems(id);
    }

    @Override
    public Page<Order> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) {
        return orderJpaRepository.findAllByUserIdAndCreatedAtBetween(userId, startDate, endDate, pageable);
    }
}
