package com.loopers.application.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsService;
import com.loopers.domain.ranking.RankingMetricsService;
import com.loopers.interfaces.consumer.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * catalog-events 메시지 처리 핸들러.
 *
 * Consumer에서 분리하여 @Transactional이 정상 동작하도록 함.
 * (self-invocation 문제 방지)
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class CatalogEventHandler {

    private final ProductMetricsService productMetricsService;
    private final RankingMetricsService rankingMetricsService;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(OutboxMessage message) {
        // 멱등성 체크: 이미 처리된 이벤트는 skip
        if (eventHandledRepository.existsByEventId(message.eventId())) {
            log.debug("[CatalogEventHandler] 이미 처리된 이벤트 skip: eventId={}", message.eventId());
            return;
        }

        Long productId = extractProductId(message.payload());
        ZonedDateTime now = ZonedDateTime.now();
        LocalDate today = now.toLocalDate();
        int hour = now.getHour();

        switch (message.eventType()) {
            case "LIKE_CREATED" -> {
                productMetricsService.incrementLikeCount(productId);
                rankingMetricsService.incrementLikeCount(productId, today, hour);
            }
            case "LIKE_CANCELLED" -> {
                productMetricsService.decrementLikeCount(productId);
                rankingMetricsService.decrementLikeCount(productId, today, hour);
            }
            case "PRODUCT_VIEWED" -> {
                productMetricsService.incrementViewCount(productId);
                rankingMetricsService.incrementViewCount(productId, today, hour);
            }
            default -> log.warn("[CatalogEventHandler] 알 수 없는 이벤트 타입: {}", message.eventType());
        }

        // 처리 기록 저장 (멱등성 보장)
        eventHandledRepository.save(new EventHandled(message.eventId(), message.eventType()));
        log.info("[CatalogEventHandler] 이벤트 처리 완료: eventId={}, eventType={}, productId={}",
                message.eventId(), message.eventType(), productId);
    }

    private Long extractProductId(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node.get("productId").asLong();
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload에서 productId 추출 실패: " + payload, e);
        }
    }
}
