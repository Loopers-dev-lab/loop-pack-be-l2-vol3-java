package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(Long id);

    Page<Order> findByUserId(Long userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable);

    Page<Order> findAll(Pageable pageable);
    boolean existsOrderItemByProductId(Long productId);
}
