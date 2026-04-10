package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.ranking.RankingRecalculationApp;
import com.loopers.infrastructure.kafka.StreamerKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingCommandConsumer {

    private static final String TOPIC = "ranking-commands";

    private final RankingRecalculationApp rankingRecalculationApp;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC,
            groupId = "commerce-streamer-ranking-command",
            containerFactory = StreamerKafkaConfig.DLQ_BATCH_LISTENER
    )
    public void consume(List<ConsumerRecord<Object, Object>> records, Acknowledgment acknowledgment) {
        for (int i = 0; i < records.size(); i++) {
            ConsumerRecord<Object, Object> record = records.get(i);
            try {
                RankingCommandPayload payload = parse(record);
                if (RankingCommandPayload.RECALCULATE.equals(payload.commandType())) {
                    if (payload.date() == null) {
                        throw new IllegalArgumentException(
                                "RankingCommandPayload.date is null for commandType=" + payload.commandType());
                    }
                    long count = rankingRecalculationApp.recalculate(payload.date());
                    log.info("[RANKING_COMMAND] RECALCULATE date={}, products={}", payload.date(), count);
                } else {
                    log.warn("[RANKING_COMMAND] 미지원 commandType={}", payload.commandType());
                }
            } catch (Exception e) {
                log.error("[RANKING_COMMAND_FAILED] offset={}, key={}", record.offset(), record.key(), e);
                throw new org.springframework.kafka.listener.BatchListenerFailedException(
                        "ranking-commands processing failed at index " + i, e, i);
            }
        }
        acknowledgment.acknowledge();
    }

    private RankingCommandPayload parse(ConsumerRecord<Object, Object> record) {
        try {
            return objectMapper.readValue((byte[]) record.value(), RankingCommandPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize RankingCommandPayload", e);
        }
    }
}
