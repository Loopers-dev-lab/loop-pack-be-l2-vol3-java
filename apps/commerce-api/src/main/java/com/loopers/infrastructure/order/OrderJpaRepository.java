package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    List<OrderModel> findAllByUserIdAndCreatedAtBetween(Long userId, ZonedDateTime startAt, ZonedDateTime endAt);

    Page<OrderModel> findAll(Pageable pageable);
}
