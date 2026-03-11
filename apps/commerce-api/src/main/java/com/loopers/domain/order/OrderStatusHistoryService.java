package com.loopers.domain.order;

import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderStatusHistoryService {

    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Transactional
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
