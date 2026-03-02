package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Page<OrderModel> findAll(Pageable pageable);
    List<OrderModel> findAllByUserIdAndPeriod(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);
    Optional<OrderModel> findDetailById(Long id);
    Optional<OrderModel> findDetailByIdAndUserId(Long id, Long userId);
}
