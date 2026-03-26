package com.loopers.application.outbox;

import com.loopers.domain.outbox.Outbox;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void createOutbox(OutboxEvent event) {
        outboxRepository.save(event.getOutbox());
    }

    @Async("outboxPublishExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishEvent(OutboxEvent event) {
        publishToKafka(event.getOutbox());
    }

    private void publishToKafka(Outbox outbox) {
        try {
            kafkaTemplate.send(
                    outbox.getEventType().getTopic(),
                    String.valueOf(outbox.getPartitionKey()),
                    outbox.getPayload()
            ).get(1, TimeUnit.SECONDS);
            outboxRepository.delete(outbox);
        } catch (Exception e) {
            log.error("[MessageRelay] Kafka 발행 실패, outboxId={}", outbox.getOutboxId(), e);
        }
    }

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void publishPendingEvents() {
        List<Outbox> pending = outboxRepository
                .findAllByCreatedAtLessThanEqualOrderByCreatedAtAsc(
                        LocalDateTime.now().minusSeconds(10),
                        Pageable.ofSize(100)
                );

        for (Outbox outbox : pending) {
            publishToKafka(outbox);
        }
    }
}
