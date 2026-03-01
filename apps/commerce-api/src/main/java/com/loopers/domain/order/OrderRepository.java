package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findByIdAndUserId(Long orderId, Long userId);

    List<Order> findByUserIdAndDateRange(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);

    Optional<Order> findById(Long orderId);

    Page<Order> findAll(Pageable pageable);
}
