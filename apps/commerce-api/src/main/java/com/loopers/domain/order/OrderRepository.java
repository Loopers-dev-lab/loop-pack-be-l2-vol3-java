package com.loopers.domain.order;

import com.loopers.domain.common.vo.RefMemberId;
import com.loopers.domain.order.vo.OrderId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderRepository {
    OrderModel save(OrderModel order);
    Optional<OrderModel> findById(Long id);
    Optional<OrderModel> findByOrderId(OrderId orderId);
    Page<OrderModel> findByRefMemberId(RefMemberId refMemberId, LocalDateTime startDateTime, LocalDateTime endDateTime, Pageable pageable);
}
