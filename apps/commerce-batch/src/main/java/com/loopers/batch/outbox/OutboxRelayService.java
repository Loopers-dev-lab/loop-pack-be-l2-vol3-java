package com.loopers.batch.outbox;

import com.loopers.infrastructure.outbox.OutboxEventModel;
import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxRelayService {

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
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                        .get(timeoutMs, TimeUnit.MILLISECONDS);
                event.markPublished(Instant.now());
            } catch (Exception e) {
                // 타임아웃·브로커 오류 등: 마킹하지 않음 → published=false 유지, 다음 폴링에서 재시도.
            }
        }
        return events.size();
    }
}

