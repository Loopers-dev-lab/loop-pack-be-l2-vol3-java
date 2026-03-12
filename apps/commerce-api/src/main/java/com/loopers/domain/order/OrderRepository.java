package com.loopers.domain.order;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderRepository {

    OrderModel save(OrderModel orderModel);

    Optional<OrderModel> findById(Long id);

    List<OrderModel> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);

    Page<OrderModel> findAll(Pageable pageable);
}
