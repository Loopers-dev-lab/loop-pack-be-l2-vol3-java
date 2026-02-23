package com.loopers.infrastructure.order.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        return orderJpaRepository.findById(orderId);
    }
}
