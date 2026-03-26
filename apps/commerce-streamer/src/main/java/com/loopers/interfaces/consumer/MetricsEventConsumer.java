package com.loopers.interfaces.consumer;

import com.loopers.application.EventProcessingService;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.Topic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsEventConsumer {
    private final EventProcessingService eventProcessingService;

    @KafkaListener(
            topics = {Topic.CATALOG_EVENTS, Topic.ORDER_EVENTS},
            groupId = "commerce-streamer-metrics",
            containerFactory = KafkaConfig.SINGLE_LISTENER
    )
    public void listen(String message, Acknowledgment ack) {
        try {
            Event<EventPayload> event = Event.fromJson(message);
            if (event == null) {
                log.warn("[MetricsEventConsumer] 이벤트 파싱 실패, message={}", message);
                ack.acknowledge();
                return;
            }

            eventProcessingService.process(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[MetricsEventConsumer] 이벤트 처리 실패 — nack 후 재처리 대기, message={}", message, e);
            ack.nack(Duration.ofSeconds(1));
        }
    }
}
