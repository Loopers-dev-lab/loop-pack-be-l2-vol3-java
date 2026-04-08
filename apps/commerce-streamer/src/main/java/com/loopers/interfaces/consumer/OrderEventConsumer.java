package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsFacade;
import com.loopers.application.ranking.RankingFacade;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.interfaces.consumer.payload.OrderCreatedEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventConsumer {

    private final ProductMetricsFacade productMetricsFacade;
    private final RankingFacade rankingFacade;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-events", containerFactory = KafkaConfig.SINGLE_LISTENER)
    public void consume(ConsumerRecord<Object, Object> record, Acknowledgment ack) {
        try {
            OrderCreatedEventPayload payload = objectMapper.readValue((String) record.value(), OrderCreatedEventPayload.class);
            productMetricsFacade.applyOrder(payload);
            rankingFacade.applyOrder(payload);
        } catch (Exception e) {
            log.error("order-events 처리 실패, skip. offset={}", record.offset(), e);
        }
        ack.acknowledge();
    }
}
