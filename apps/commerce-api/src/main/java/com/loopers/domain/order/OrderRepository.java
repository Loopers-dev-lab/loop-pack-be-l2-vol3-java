package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(UUID id);

    Page<Order> findByUserId(UUID userId, ZonedDateTime startAt, ZonedDateTime endAt, Pageable pageable);

    Page<Order> findAll(Pageable pageable);
    boolean existsOrderItemByProductId(UUID productId);
}
