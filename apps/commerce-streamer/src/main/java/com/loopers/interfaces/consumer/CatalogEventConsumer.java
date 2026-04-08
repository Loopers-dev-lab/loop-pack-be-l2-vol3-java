package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.metrics.ProductMetricsAppService;
import com.loopers.application.ranking.RankingAppService;
import com.loopers.confg.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventConsumer {
    private final ProductMetricsAppService productMetricsAppService;
    private final RankingAppService rankingAppService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "catalog-events",
            groupId = "catalog-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<String, String>> messages, Acknowledgment acknowledgment) {
        boolean hasFailure = false;
        for (ConsumerRecord<String, String> record : messages) {
            try {
                JsonNode node = objectMapper.readTree(record.value());
                String eventId = record.topic() + ":" + record.partition() + ":" + record.offset();

                String eventType = node.has("eventType") ? node.get("eventType").asText() : "";
                Long productId = node.get("productId").asLong();
                ZonedDateTime occurredAt = ZonedDateTime.parse(node.get("occurredAt").asText());

                switch (eventType) {
                    case "ProductViewed" -> {
                        productMetricsAppService.handleProductViewed(eventId, productId, occurredAt);
                        rankingAppService.updateViewRanking(productId);
                    }
                    case "LikeToggled" -> {
                        boolean liked = node.get("liked").asBoolean();
                        productMetricsAppService.handleLikeToggled(eventId, productId, liked, occurredAt);
                        if (liked) {
                            rankingAppService.updateLikeRanking(productId);
                        }
                    }
                    default ->
                            log.warn("알 수 없는 catalog 이벤트: eventType={}", eventType);
                }
            } catch (JsonProcessingException e) {
                log.error("catalog-events 메시지 파싱 실패 (skip): {}", record.value(), e);
            } catch (Exception e) {
                log.error("catalog-events 처리 실패: {}", record.value(), e);
                hasFailure = true;
            }
        }
        if (!hasFailure) {
            acknowledgment.acknowledge();
        }
    }
}
