package com.loopers.application.payment;

import com.loopers.application.metrics.ProductMetricsAckPublisher;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.payment.PaymentEventLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentStatusChangedConsumerService {

    private final EventHandledRepository eventHandledRepository;
    private final PaymentEventLogRepository paymentEventLogRepository;
    private final ProductMetricsAckPublisher productMetricsAckPublisher;

    public PaymentStatusChangedConsumerService(
            EventHandledRepository eventHandledRepository,
            PaymentEventLogRepository paymentEventLogRepository,
            ProductMetricsAckPublisher productMetricsAckPublisher
    ) {
        this.eventHandledRepository = eventHandledRepository;
        this.paymentEventLogRepository = paymentEventLogRepository;
        this.productMetricsAckPublisher = productMetricsAckPublisher;
    }

    @Transactional
    public void consume(String consumerGroup, PaymentStatusChangedEventMessage message) {
        boolean inserted = eventHandledRepository.markHandledIfAbsent(consumerGroup, message.eventId());
        if (!inserted) {
            return;
        }
        paymentEventLogRepository.save(message);
        productMetricsAckPublisher.publish(message.eventId(), consumerGroup);
    }
}
