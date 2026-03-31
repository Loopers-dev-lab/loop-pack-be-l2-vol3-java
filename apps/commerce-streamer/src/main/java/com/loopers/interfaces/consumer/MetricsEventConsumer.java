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
    public void consume(String message, Acknowledgment ack) {
        Event<EventPayload> event = Event.fromJson(message);
        if (event == null) {
            log.warn("[MetricsEventConsumer] 이벤트 파싱 실패, message={}", message);
            ack.acknowledge();
            return;
        }

        eventProcessingService.process(event);
        ack.acknowledge();
    }
}
