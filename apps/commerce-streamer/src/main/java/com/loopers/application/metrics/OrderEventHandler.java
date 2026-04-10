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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * order-events 메시지 처리 핸들러.
 *
 * ORDER_CREATED 이벤트의 items 배열을 파싱하여
 * 상품별 order_revenue를 ranking_metrics에 적재한다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventHandler {

    private final ProductMetricsService productMetricsService;
    private final RankingMetricsService rankingMetricsService;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(OutboxMessage message) {
        // 멱등성 체크
        if (eventHandledRepository.existsByEventId(message.eventId())) {
            log.debug("[OrderEventHandler] 이미 처리된 이벤트 skip: eventId={}", message.eventId());
            return;
        }

        if ("ORDER_CREATED".equals(message.eventType())) {
            processOrderCreated(message);
        } else {
            log.warn("[OrderEventHandler] 알 수 없는 이벤트 타입: {}", message.eventType());
        }

        // 처리 기록 저장 (멱등성 보장)
        eventHandledRepository.save(new EventHandled(message.eventId(), message.eventType()));
    }

    private void processOrderCreated(OutboxMessage message) {
        JsonNode node = parsePayload(message.payload());
        JsonNode items = node.get("items");

        // items가 없는 기존 메시지는 랭킹 집계 없이 통과 (하위 호환)
        if (items == null || !items.isArray()) {
            log.info("[OrderEventHandler] items 없는 ORDER_CREATED 이벤트: eventId={}", message.eventId());
            return;
        }

        ZonedDateTime now = ZonedDateTime.now();
        LocalDate today = now.toLocalDate();
        int hour = now.getHour();

        for (JsonNode item : items) {
            long productId = item.get("productId").asLong();
            int price = item.get("price").asInt();
            int quantity = item.get("quantity").asInt();
            BigDecimal revenue = BigDecimal.valueOf((long) price * quantity);
            rankingMetricsService.addOrderRevenue(productId, today, hour, revenue);
            productMetricsService.incrementOrderCount(productId);
        }

        log.info("[OrderEventHandler] 주문 생성 이벤트 처리 완료: eventId={}, itemCount={}",
                message.eventId(), items.size());
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload 파싱 실패: " + payload, e);
        }
    }
}
