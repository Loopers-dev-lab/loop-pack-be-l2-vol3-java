package com.loopers.infrastructure.order;

import com.loopers.domain.order.OrderHistory;
import com.loopers.domain.order.OrderHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OrderHistoryRepositoryImpl implements OrderHistoryRepository {

    private final OrderHistoryJpaRepository orderHistoryJpaRepository;

    @Override
    public OrderHistory save(OrderHistory orderHistory) {
        return orderHistoryJpaRepository.save(orderHistory);
    }

    @Override
    public List<OrderHistory> findAllByOrderId(Long orderId) {
        return orderHistoryJpaRepository.findAllByOrderIdOrderByCreatedAtAsc(orderId);
    }
}
