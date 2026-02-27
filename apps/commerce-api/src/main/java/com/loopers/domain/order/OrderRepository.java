package com.loopers.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime from, ZonedDateTime to);

    Page<Order> findAll(Pageable pageable);
}
