package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingApp;
import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.infrastructure.kafka.StreamerKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewEventConsumer {

    private static final String TOPIC = "view-events";
    private static final String SUPPORTED_EVENT_TYPE = "ViewedEvent";

    private final RankingApp rankingApp;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC,
            groupId = "commerce-streamer-view",
            containerFactory = StreamerKafkaConfig.DLQ_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<Object, Object> record = records.get(i);
            try {
                CatalogEventPayload payload = parse(record);
                if (!SUPPORTED_EVENT_TYPE.equals(payload.eventType())) {
                    log.warn("[VIEW_EVENT] 미지원 eventType={}, offset={} — 건너뜀",
                            payload.eventType(), record.offset());
                    continue;
                }
                processIfNotHandled(payload);
            } catch (Exception e) {
                log.error("[VIEW_EVENT_FAILED] offset={}, key={}", record.offset(), record.key(), e);
                throw new org.springframework.kafka.listener.BatchListenerFailedException(
                        "view-events processing failed at index " + i, e, i);
            }
        }
        acknowledgment.acknowledge();
    }

    @Transactional
    public void processIfNotHandled(CatalogEventPayload payload) {
        if (eventHandledRepository.existsByEventId(payload.eventId())) {
            return;
        }
        try {
            rankingApp.applyViewScore(payload.productDbId(), payload.likedAt());
        } catch (Exception e) {
            log.warn("[RANKING_BEST_EFFORT] View 랭킹 반영 실패 — productDbId={}", payload.productDbId(), e);
        }
        eventHandledRepository.save(EventHandledModel.create(payload.eventId(), TOPIC));
    }

    private CatalogEventPayload parse(ConsumerRecord<Object, Object> record) {
        try {
            return objectMapper.readValue((byte[]) record.value(), CatalogEventPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize CatalogEventPayload", e);
        }
    }
}
