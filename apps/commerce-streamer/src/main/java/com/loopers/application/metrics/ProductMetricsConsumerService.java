package com.loopers.application.metrics;

import com.loopers.contract.kafka.ProductMetricsEventMessage;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetricsDailyRepository;
import com.loopers.infrastructure.metrics.ProductMetricsHourlyRepository;
import com.loopers.infrastructure.metrics.ProductMetricsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductMetricsConsumerService {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricsDailyRepository productMetricsDailyRepository;
    private final ProductMetricsHourlyRepository productMetricsHourlyRepository;
    private final ProductMetricsAckPublisher productMetricsAckPublisher;

    public ProductMetricsConsumerService(
            EventHandledRepository eventHandledRepository,
            ProductMetricsRepository productMetricsRepository,
            ProductMetricsDailyRepository productMetricsDailyRepository,
            ProductMetricsHourlyRepository productMetricsHourlyRepository,
            ProductMetricsAckPublisher productMetricsAckPublisher
    ) {
        this.eventHandledRepository = eventHandledRepository;
        this.productMetricsRepository = productMetricsRepository;
        this.productMetricsDailyRepository = productMetricsDailyRepository;
        this.productMetricsHourlyRepository = productMetricsHourlyRepository;
        this.productMetricsAckPublisher = productMetricsAckPublisher;
    }

    @Transactional
    public void consume(String consumerGroup, ProductMetricsEventMessage message) {
        boolean inserted = eventHandledRepository.markHandledIfAbsent(consumerGroup, message.eventId());
        if (!inserted) {
            return;
        }
        productMetricsRepository.upsert(message);
        productMetricsDailyRepository.upsert(message);
        productMetricsHourlyRepository.upsert(message);
        productMetricsAckPublisher.publish(message.eventId(), consumerGroup);
    }
}
