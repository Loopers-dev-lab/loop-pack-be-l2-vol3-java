package com.loopers.collector.interfaces;

import com.loopers.collector.application.ProductEventCollectorService;
import com.loopers.collector.config.ProductEventConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ProductEventsCollectorListener {

    private final ProductEventCollectorService collectorService;

    public ProductEventsCollectorListener(ProductEventCollectorService collectorService) {
        this.collectorService = collectorService;
    }

    @KafkaListener(
            topics = "${collector.product.topic-name:product-events}",
            containerFactory = ProductEventConsumerConfig.PRODUCT_EVENT_LISTENER
    )
    public void listen(ConsumerRecord<Object, Object> record, Acknowledgment acknowledgment) {
        collectorService.process(record);
        acknowledgment.acknowledge();
    }
}
