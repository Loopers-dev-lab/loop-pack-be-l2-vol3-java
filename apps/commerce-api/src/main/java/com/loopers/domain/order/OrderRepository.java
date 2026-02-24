package com.loopers.domain.order;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long orderId);

    Optional<Order> findByIdWithItems(Long orderId);

    Slice<Order> findAllByUserIdAndOrderedAtBetween(Long userId, LocalDateTime start, LocalDateTime end, Pageable pageable);
}
