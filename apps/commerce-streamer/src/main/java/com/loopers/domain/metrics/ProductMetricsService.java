package com.loopers.domain.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.idempotency.EventHandled;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.support.kafka.KafkaOutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Slf4j
@RequiredArgsConstructor
@Component
@Transactional
public class ProductMetricsService {

    private static final double WEIGHT_VIEW = 0.1;
    private static final double WEIGHT_LIKE = 0.2;
    private static final double WEIGHT_SOLD = 0.6;

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledRepository eventHandledRepository;
    private final RankingRepository rankingRepository;
    private final ObjectMapper objectMapper;

    public void handle(KafkaOutboxMessage message) {
        if (eventHandledRepository.existsByEventId(message.eventId())) {
            log.info("중복 이벤트 스킵. eventId={}, eventType={}", message.eventId(), message.eventType());
            return;
        }

        ZonedDateTime occurredAt = message.occurredAt() != null
            ? ZonedDateTime.parse(message.occurredAt())
            : ZonedDateTime.now();

        LocalDate rankingDate = occurredAt.toLocalDate();

        switch (message.eventType()) {
            case "LIKE_CREATED" -> {
                LikePayload payload = parsePayload(message.payload(), LikePayload.class);
                productMetricsRepository.incrementLikeCount(payload.productId(), occurredAt);
                rankingRepository.incrementScore(payload.productId(), WEIGHT_LIKE, rankingDate);
            }
            case "LIKE_DELETED" -> {
                LikePayload payload = parsePayload(message.payload(), LikePayload.class);
                productMetricsRepository.decrementLikeCount(payload.productId(), occurredAt);
                rankingRepository.incrementScore(payload.productId(), -WEIGHT_LIKE, rankingDate);
            }
            case "PRODUCT_SOLD" -> {
                ProductSoldPayload payload = parsePayload(message.payload(), ProductSoldPayload.class);
                productMetricsRepository.incrementSalesCount(payload.productId(), occurredAt);
                double score = WEIGHT_SOLD * Math.log1p(payload.amount());
                rankingRepository.incrementScore(payload.productId(), score, rankingDate);
            }
            case "PRODUCT_VIEWED" -> {
                ViewPayload payload = parsePayload(message.payload(), ViewPayload.class);
                productMetricsRepository.incrementViewCount(payload.productId(), occurredAt);
                rankingRepository.incrementScore(payload.productId(), WEIGHT_VIEW, rankingDate);
            }
            default -> log.warn("알 수 없는 eventType: {}", message.eventType());
        }

        eventHandledRepository.save(new EventHandled(message.eventId()));
    }

    private <T> T parsePayload(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 페이로드 역직렬화 실패", e);
        }
    }

    public record LikePayload(Long userId, Long productId) {}

    public record ProductSoldPayload(Long productId, Long orderId, long amount) {}

    public record ViewPayload(Long productId, String userId, String userAgent) {}
}
