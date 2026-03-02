package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findActiveById(Long id);
    Page<Order> findAllActiveByUserId(Long userId, Pageable pageable);
    Page<Order> findAllActive(Pageable pageable);
}
