package com.loopers.collector.interfaces;

import com.loopers.collector.application.ProductEventCollectorService;
import com.loopers.collector.config.ProductEventConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 도메인을 제외한 상품·주문·회원 계열 이벤트 수신.
 */
@Component
public class DomainEventsCollectorListener {

    private final ProductEventCollectorService collectorService;

    public DomainEventsCollectorListener(ProductEventCollectorService collectorService) {
        this.collectorService = collectorService;
    }

    @KafkaListener(
            topics = "${collector.product.topic-name:product-events}",
            containerFactory = ProductEventConsumerConfig.PRODUCT_EVENT_LISTENER
    )
    public void onProductEvents(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        collectorService.process(record);
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = "${collector.order.topic-name:order-events}",
            containerFactory = ProductEventConsumerConfig.PRODUCT_EVENT_LISTENER
    )
    public void onOrderEvents(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        collectorService.process(record);
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = "${collector.user.topic-name:user-events}",
            containerFactory = ProductEventConsumerConfig.PRODUCT_EVENT_LISTENER
    )
    public void onUserEvents(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        collectorService.process(record);
        acknowledgment.acknowledge();
    }
}
