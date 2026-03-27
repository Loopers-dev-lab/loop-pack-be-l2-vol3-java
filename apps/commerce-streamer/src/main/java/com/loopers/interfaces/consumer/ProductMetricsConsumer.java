package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.support.kafka.KafkaOutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductMetricsConsumer {

    private final ProductMetricsService productMetricsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"product.like.events", "product.payment.events", "product.view.events"},
        groupId = "product-metrics-consumer",
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> message : messages) {
            try {
                byte[] valueBytes = (byte[]) message.value();
                KafkaOutboxMessage kafkaMessage = objectMapper.readValue(valueBytes, KafkaOutboxMessage.class);
                productMetricsService.handle(kafkaMessage);
            } catch (DataIntegrityViolationException e) {
                log.info("중복 이벤트 스킵 (DataIntegrityViolation). topic={}, offset={}", message.topic(), message.offset());
            } catch (Exception e) {
                log.warn("Kafka 메시지 처리 실패. topic={}, offset={}, 이유={}", message.topic(), message.offset(), e.getMessage());
            }
        }
        acknowledgment.acknowledge();
    }
}