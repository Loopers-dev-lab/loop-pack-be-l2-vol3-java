package com.loopers.domain.order.repository;

import com.loopers.domain.order.model.Orders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Orders save(Orders orders);

    Optional<Orders> findById(Long id);

    List<Orders> findByMemberIdAndCreatedAtBetween(Long memberId, LocalDateTime startAt, LocalDateTime endAt);
}
