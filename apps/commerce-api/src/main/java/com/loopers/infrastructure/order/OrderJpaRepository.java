package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    List<OrderModel> findAllByUserIdAndCreatedAtBetween(
        Long userId, ZonedDateTime startAt, ZonedDateTime endAt
    );

    Page<OrderModel> findAll(Pageable pageable);
}
