package com.loopers.domain.order;

import com.loopers.support.page.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Long save(Order order);

    Optional<Order> findById(Long orderId);

    Optional<Order> findByOrderId(String orderId);

    PageResponse<Order> findAll(Pageable pageable);

    List<Order> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);
}
