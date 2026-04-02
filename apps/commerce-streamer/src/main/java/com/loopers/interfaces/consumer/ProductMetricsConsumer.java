package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsProcessor;
import com.loopers.confg.kafka.KafkaConfig;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductMetricsConsumer {

    private final ProductMetricsProcessor processor;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"catalog-events", "order-events"},
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (ConsumerRecord<Object, Object> record : records) {
            try {
                processRecord(record);
            } catch (Exception e) {
                log.error("이벤트 처리 실패. topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processRecord(ConsumerRecord<Object, Object> record) throws Exception {
        String baseEventId = record.topic() + "-" + record.partition() + "-" + record.offset();
        byte[] bytes = (byte[]) record.value();
        Map<String, Object> payload = objectMapper.readValue(bytes, new TypeReference<>() {});

        String eventType = (String) payload.get("type");
        ZonedDateTime occurredAt = ZonedDateTime.parse((String) payload.get("occurredAt"));

        if ("ORDER_CONFIRMED".equals(eventType)) {
            List<Integer> productIds = (List<Integer>) payload.get("productIds");
            for (Integer productId : productIds) {
                String eventId = baseEventId + "-" + productId;
                processor.process(eventId, eventType, productId.longValue(), occurredAt);
            }
        } else {
            Long productId = ((Number) payload.get("productId")).longValue();
            processor.process(baseEventId, eventType, productId, occurredAt);
        }
    }
}
