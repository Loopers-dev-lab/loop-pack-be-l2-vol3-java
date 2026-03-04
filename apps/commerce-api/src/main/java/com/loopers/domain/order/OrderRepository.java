package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface OrderRepository {

    // Command
    Order save(Order order);

    // Query
    Optional<Order> findByIdWithItems(Long id);

    Page<Order> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable);

    Page<Order> findAll(Pageable pageable);
}
