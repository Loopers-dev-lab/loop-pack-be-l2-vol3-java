package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsFacade;
import com.loopers.application.ranking.RankingFacade;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.interfaces.consumer.payload.CatalogEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventConsumer {

    private final ProductMetricsFacade productMetricsFacade;
    private final RankingFacade rankingFacade;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "catalog-events", containerFactory = KafkaConfig.SINGLE_LISTENER)
    public void consume(ConsumerRecord<Object, Object> record, Acknowledgment ack) {
        try {
            CatalogEventPayload payload = objectMapper.readValue((String) record.value(), CatalogEventPayload.class);
            productMetricsFacade.applyLike(payload);
            rankingFacade.applyLike(payload);
        } catch (Exception e) {
            log.error("catalog-events 처리 실패, skip. offset={}", record.offset(), e);
        }
        ack.acknowledge();
    }
}
