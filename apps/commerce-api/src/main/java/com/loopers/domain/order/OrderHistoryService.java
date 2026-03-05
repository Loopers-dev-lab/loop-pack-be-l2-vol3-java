package com.loopers.domain.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderHistoryService {

    private final OrderHistoryRepository orderHistoryRepository;

    @Transactional
    public OrderHistory recordHistory(Long orderId, OrderStatus previousStatus, OrderStatus newStatus, String description) {
        OrderHistory history = OrderHistory.create(orderId, previousStatus, newStatus, description);
        return orderHistoryRepository.save(history);
    }

    public List<OrderHistory> getHistoriesByOrderId(Long orderId) {
        return orderHistoryRepository.findAllByOrderId(orderId);
    }
}
