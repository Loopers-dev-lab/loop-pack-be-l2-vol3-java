package com.loopers.application.metrics;

import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetricsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductMetricsConsumerService {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricsAckPublisher productMetricsAckPublisher;

    public ProductMetricsConsumerService(
            EventHandledRepository eventHandledRepository,
            ProductMetricsRepository productMetricsRepository,
            ProductMetricsAckPublisher productMetricsAckPublisher
    ) {
        this.eventHandledRepository = eventHandledRepository;
        this.productMetricsRepository = productMetricsRepository;
        this.productMetricsAckPublisher = productMetricsAckPublisher;
    }

    @Transactional
    public void consume(String consumerGroup, ProductMetricsEventMessage message) {
        boolean inserted = eventHandledRepository.markHandledIfAbsent(consumerGroup, message.eventId());
        if (!inserted) {
            return;
        }
        productMetricsRepository.upsert(message);
        productMetricsAckPublisher.publish(message.eventId(), consumerGroup);
    }
}
