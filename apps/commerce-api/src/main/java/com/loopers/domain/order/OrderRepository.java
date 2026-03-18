package com.loopers.domain.order;

import com.loopers.domain.PageResult;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByIdForUpdate(Long id);

    Optional<Order> findByIdWithItems(Long id);

    PageResult<Order> findByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt, int page, int size);

    PageResult<Order> findAll(int page, int size);
}
