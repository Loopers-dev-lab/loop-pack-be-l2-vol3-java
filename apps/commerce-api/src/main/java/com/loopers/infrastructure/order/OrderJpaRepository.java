package com.loopers.infrastructure.order;

import com.loopers.domain.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;

public interface OrderJpaRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByUserIdAndCreatedAtBetweenAndDeletedAtIsNull(
            Long userId, ZonedDateTime from, ZonedDateTime to);

    Page<Order> findAllByDeletedAtIsNull(Pageable pageable);
}
