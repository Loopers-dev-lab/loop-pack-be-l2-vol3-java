package com.loopers.domain.repository;

import com.loopers.domain.model.order.Order;
import com.loopers.domain.model.user.UserId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findAllByUserId(UserId userId);

    List<Order> findAllByUserIdAndDateRange(UserId userId, LocalDateTime startAt, LocalDateTime endAt);

    List<Order> findAll();
}
