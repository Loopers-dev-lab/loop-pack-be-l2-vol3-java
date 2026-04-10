package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.eventhandled.EventHandledService;
import com.loopers.domain.metrics.CatalogEventMessage;
import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.domain.ranking.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final double WEIGHT_VIEW = 0.1;
    private static final double WEIGHT_LIKE = 0.2;
    private static final double WEIGHT_ORDER = 0.7;

    private final ProductMetricsService productMetricsService;
    private final EventHandledService eventHandledService;
    private final RankingRepository rankingRepository;
    private final ObjectMapper objectMapper;

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

        String today = LocalDate.now().format(DATE_FORMAT);

        switch (message.eventType()) {
            case "PRODUCT_LIKED" -> {
                Long productId = Long.valueOf(message.aggregateId());
                productMetricsService.increaseLikes(productId);
                rankingRepository.incrementScore(today, productId, WEIGHT_LIKE);
            }
            case "PRODUCT_UNLIKED" -> {
                Long productId = Long.valueOf(message.aggregateId());
                productMetricsService.decreaseLikes(productId);
                rankingRepository.incrementScore(today, productId, -WEIGHT_LIKE);
            }
            case "PRODUCT_VIEWED" -> {
                Long productId = Long.valueOf(message.aggregateId());
                productMetricsService.increaseViews(productId);
                rankingRepository.incrementScore(today, productId, WEIGHT_VIEW);
            }
            case "ORDER_COMPLETED" -> processOrderCompleted(message, today);
            default -> log.warn("알 수 없는 이벤트 타입: eventType={}", message.eventType());
        }

        eventHandledService.markAsHandled(message.eventId());
    }

    private void processOrderCompleted(CatalogEventMessage message, String today) {
        try {
            JsonNode payload = objectMapper.readTree(message.payload());
            JsonNode items = payload.get("items");
            if (items == null || !items.isArray()) {
                log.warn("ORDER_COMPLETED payload에 items 없음: eventId={}", message.eventId());
                return;
            }
            for (JsonNode item : items) {
                Long productId = item.get("productId").asLong();
                int quantity = item.get("quantity").asInt();
                productMetricsService.increaseOrders(productId, quantity);
                rankingRepository.incrementScore(today, productId, WEIGHT_ORDER * quantity);
            }
        } catch (JsonProcessingException e) {
            log.error("ORDER_COMPLETED payload 파싱 실패: eventId={}", message.eventId(), e);
        }
    }
}
