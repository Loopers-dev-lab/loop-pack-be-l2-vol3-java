package com.loopers.application.order;

import com.loopers.application.metrics.ProductMetricsAckPublisher;
import com.loopers.contract.kafka.OrderCancelRequestedOutboxMessage;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.order.OrderEventLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCancelRequestedConsumerService {

    private final EventHandledRepository eventHandledRepository;
    private final OrderEventLogRepository orderEventLogRepository;
    private final ProductMetricsAckPublisher productMetricsAckPublisher;

    public OrderCancelRequestedConsumerService(
            EventHandledRepository eventHandledRepository,
            OrderEventLogRepository orderEventLogRepository,
            ProductMetricsAckPublisher productMetricsAckPublisher
    ) {
        this.eventHandledRepository = eventHandledRepository;
        this.orderEventLogRepository = orderEventLogRepository;
        this.productMetricsAckPublisher = productMetricsAckPublisher;
    }

    @Transactional
    public void consume(String consumerGroup, OrderCancelRequestedOutboxMessage message) {
        boolean inserted = eventHandledRepository.markHandledIfAbsent(consumerGroup, message.eventId());
        if (!inserted) {
            return;
        }
        orderEventLogRepository.saveOrderCancelRequested(message);
        productMetricsAckPublisher.publish(message.eventId(), consumerGroup);
    }
}
