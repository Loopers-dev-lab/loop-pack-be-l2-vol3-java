package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;

public interface OrderJpaRepository extends JpaRepository<OrderModel, Long> {

    /** orderedAt >= start and orderedAt < end, orderedAt DESC */
    Page<OrderModel> findByUserIdAndOrderedAtGreaterThanEqualAndOrderedAtLessThanOrderByOrderedAtDesc(
            Long userId,
            ZonedDateTime start,
            ZonedDateTime end,
            Pageable pageable
    );
}
