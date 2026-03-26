package com.loopers.batch.outbox;

import com.loopers.infrastructure.outbox.OutboxEventModel;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxRelayService {

    private static final String HEADER_EVENT_ID = "eventId";
    private static final String HEADER_EVENT_TYPE = "eventType";

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final OutboxRelayProperties relayProperties;

    public OutboxRelayService(
            OutboxJpaRepository outboxJpaRepository,
            KafkaTemplate<Object, Object> kafkaTemplate,
            OutboxRelayProperties relayProperties) {
        this.outboxJpaRepository = outboxJpaRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.relayProperties = relayProperties;
    }

    @Transactional
    public int relayOnce(int batchSize) {
        List<OutboxEventModel> events = outboxJpaRepository.findPendingForUpdateSkipLocked(batchSize);
        for (OutboxEventModel event : events) {
            // send 결과는 브로커/네트워크 상황에 따라 비동기다.
            // Step 2는 "릴레이가 발행 시도 + 발행 성공 시 마킹"이 목표이므로, 여기서는 get()으로 성공을 확인한다.
            try {
                long timeoutMs = relayProperties.sendAckTimeout().toMillis();
                String envelopeJson = buildEnvelopeJson(event);
                ProducerRecord<Object, Object> record = new ProducerRecord<>(
                        event.getTopic(),
                        null,
                        event.getPartitionKey(),
                        envelopeJson
                );
                record.headers().add(HEADER_EVENT_ID, event.getEventId().getBytes(StandardCharsets.UTF_8));
                record.headers().add(HEADER_EVENT_TYPE, event.getEventType().getBytes(StandardCharsets.UTF_8));

                kafkaTemplate.send(record).get(timeoutMs, TimeUnit.MILLISECONDS);
                event.markPublished(Instant.now());
            } catch (Exception e) {
                // 타임아웃·브로커 오류 등: 마킹하지 않음 → published=false 유지, 다음 폴링에서 재시도.
            }
        }
        return events.size();
    }

    private static String buildEnvelopeJson(OutboxEventModel event) {
        // 최소 필드만 안정적으로 유지; 컨슈머는 멱등성 처리를 eventId에 의존한다.
        return "{\"eventId\":\"" + escapeJson(event.getEventId()) + "\""
                + ",\"eventType\":\"" + escapeJson(event.getEventType()) + "\""
                + ",\"occurredAt\":\"" + event.getOccurredAt() + "\""
                + ",\"partitionKey\":\"" + escapeJson(event.getPartitionKey()) + "\""
                + ",\"data\":" + event.getPayload()
                + "}";
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

