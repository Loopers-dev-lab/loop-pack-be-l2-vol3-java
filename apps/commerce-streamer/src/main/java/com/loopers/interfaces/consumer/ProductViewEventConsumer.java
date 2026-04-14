package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsFacade;
import com.loopers.application.ranking.RankingFacade;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.interfaces.consumer.payload.ProductViewEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductViewEventConsumer {

    private final ProductMetricsFacade productMetricsFacade;
    private final RankingFacade rankingFacade;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "product-view-events", containerFactory = KafkaConfig.SINGLE_LISTENER)
    public void consume(ConsumerRecord<Object, Object> record, Acknowledgment ack) {
        try {
            ProductViewEventPayload payload = objectMapper.readValue((String) record.value(), ProductViewEventPayload.class);
            productMetricsFacade.applyView(payload);
            rankingFacade.applyView(payload);
        } catch (Exception e) {
            log.error("product-view-events 처리 실패, skip. offset={}", record.offset(), e);
        }
        ack.acknowledge();
    }
}
