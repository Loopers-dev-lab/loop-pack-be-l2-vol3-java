package com.loopers.interfaces.consumer;

import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.eventhandled.EventHandledService;
import com.loopers.domain.metrics.CatalogEventMessage;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    private final ProductMetricsService productMetricsService;
    private final EventHandledService eventHandledService;

    @KafkaListener(
            topics = "catalog-events",
            groupId = "catalog-metrics-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<CatalogEventMessage> messages, Acknowledgment acknowledgment) {
        for (CatalogEventMessage message : messages) {
            try {
                processMessage(message);
            } catch (Exception e) {
                log.error("카탈로그 이벤트 처리 실패: eventId={}", message.eventId(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processMessage(CatalogEventMessage message) {
        if (eventHandledService.isAlreadyHandled(message.eventId())) {
            log.debug("이미 처리된 이벤트: eventId={}", message.eventId());
            return;
        }

        Long productId = Long.valueOf(message.aggregateId());

        switch (message.eventType()) {
            case "PRODUCT_LIKED" -> productMetricsService.increaseLikes(productId);
            case "PRODUCT_UNLIKED" -> productMetricsService.decreaseLikes(productId);
            default -> log.warn("알 수 없는 이벤트 타입: eventType={}", message.eventType());
        }

        eventHandledService.markAsHandled(message.eventId());
    }
}
