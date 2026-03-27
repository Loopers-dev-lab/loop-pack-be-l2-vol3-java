package com.loopers.batch.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "relay.kafka-outbox.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaOutboxRelay {

    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${relay.kafka-outbox.fixed-delay-ms:5000}")
    public void relay() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, event_id, event_type, topic, payload, partition_key, created_at FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 100"
        );

        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String eventId = (String) row.get("event_id");
            String eventType = (String) row.get("event_type");
            String topic = (String) row.get("topic");
            String payload = (String) row.get("payload");
            String partitionKey = (String) row.get("partition_key");
            String occurredAt = String.valueOf(row.get("created_at"));

            KafkaOutboxRelayMessage message = new KafkaOutboxRelayMessage(eventId, eventType, payload, occurredAt);

            try {
                kafkaTemplate.send(topic, partitionKey != null ? partitionKey : eventId, message).get(5, TimeUnit.SECONDS);
                jdbcTemplate.update(
                    "UPDATE outbox_events SET status = 'SENT', updated_at = NOW() WHERE id = ?",
                    id
                );
            } catch (Exception e) {
                log.warn("Outbox 이벤트 Kafka 발행 실패. id={}, eventId={}, eventType={}, 이유={}", id, eventId, eventType, e.getMessage());
            }
        }
    }

    public record KafkaOutboxRelayMessage(String eventId, String eventType, String payload, String occurredAt) {}
}