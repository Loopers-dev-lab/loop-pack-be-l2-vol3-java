package com.loopers.domain.order;

import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    public void recordHistory(Long orderId, OrderStatus status) {
        OrderStatusHistory history = OrderStatusHistory.create(
            null,
            orderId,
            status,
            ZonedDateTime.now()
        );
        orderStatusHistoryRepository.save(history);
    }
}
