package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsConsumerService;
import com.loopers.contract.kafka.ProductMetricsEventMessage;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductMetricsKafkaConsumer {

    private static final String CONSUMER_GROUP = "commerce-collector-product-metrics";

    private final ProductMetricsConsumerService productMetricsConsumerService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"${loopers.kafka.topic.product-metrics:commerce.product.metrics.v1}"},
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void metricsListener(
        List<ConsumerRecord<Object,Object>> messages,
        Acknowledgment acknowledgment
    ){
        for (ConsumerRecord<Object, Object> message : messages) {
            try {
                ProductMetricsEventMessage eventMessage = toEventMessage(message.value());
                productMetricsConsumerService.consume(CONSUMER_GROUP, eventMessage);
            } catch (Exception e) {
                log.warn("product_metrics_consume_failed topic={} partition={} offset={}",
                        message.topic(), message.partition(), message.offset(), e);
                throw new IllegalStateException("Product metrics consume failed", e);
            }
        }
        acknowledgment.acknowledge();
    }

    private ProductMetricsEventMessage toEventMessage(Object rawValue) throws Exception {
        if (rawValue instanceof byte[] bytes) {
            return objectMapper.readValue(bytes, ProductMetricsEventMessage.class);
        }
        if (rawValue instanceof String value) {
            return objectMapper.readValue(value, ProductMetricsEventMessage.class);
        }
        return objectMapper.convertValue(rawValue, ProductMetricsEventMessage.class);
    }
}
