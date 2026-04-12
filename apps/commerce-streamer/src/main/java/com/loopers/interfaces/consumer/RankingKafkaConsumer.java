package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScorePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingKafkaConsumer {

    private static final DateTimeFormatter DATE_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingScorePolicy rankingScorePolicy;
    private final RankingRepository rankingRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = {"${ranking.kafka.topic-name}"},
            groupId = "ranking-consumer",
            containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
        String dateKey = LocalDate.now().format(DATE_KEY_FORMAT);

        for (ConsumerRecord<Object, Object> record : messages) {
            try {
                processRecord(record, dateKey);
            } catch (Exception e) {
                log.error("[RankingKafkaConsumer] 이벤트 처리 실패 - key={}, value={}", record.key(), record.value(), e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void processRecord(ConsumerRecord<Object, Object> record, String dateKey) throws JsonProcessingException {
        JsonNode payload = objectMapper.readTree(record.value().toString());

        String eventType = payload.path("eventType").asText("");
        Long productId = payload.path("productId").asLong(0);
        Long price = payload.path("price").asLong(0);
        int quantity = payload.path("quantity").asInt(0);

        if (productId == 0) {
            log.warn("[RankingKafkaConsumer] productId가 없는 이벤트 스킵 - payload={}", payload);
            return;
        }

        double score = rankingScorePolicy.calculate(eventType, price, quantity);
        if (score > 0) {
            rankingRepository.incrementScore(dateKey, productId, score);
            log.debug("[RankingKafkaConsumer] 점수 반영 - productId={}, eventType={}, score={}", productId, eventType, score);
        }
    }
}
