package com.loopers.collector.interfaces;

import com.loopers.collector.application.ProductEventCollectorService;
import com.loopers.confg.kafka.KafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductEventsCollectorListener {

    private final ProductEventCollectorService collectorService;

    public ProductEventsCollectorListener(ProductEventCollectorService collectorService) {
        this.collectorService = collectorService;
    }

    @KafkaListener(
            topics = "${collector.product.topic-name:product-events}",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void listen(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            collectorService.process(record);
        }
        acknowledgment.acknowledge();
    }
}
