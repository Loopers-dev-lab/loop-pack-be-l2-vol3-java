package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderHistoryJpaRepository extends JpaRepository<OrderHistory, Long> {
    List<OrderHistory> findAllByOrderIdOrderByCreatedAtAsc(Long orderId);
}
