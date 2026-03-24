package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@Component
public class OutboxPublisher {

    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final PlatformTransactionManager transactionManager;

    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 5;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        List<OutboxEvent> pendingEvents = txTemplate.execute(status ->
            outboxJpaRepository.findUnpublished(PageRequest.of(0, BATCH_SIZE)));

        if (pendingEvents == null || pendingEvents.isEmpty()) {
            return;
        }

        List<OutboxEvent> publishedEvents = new ArrayList<>();
        int failedCount = 0;
        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished();
                publishedEvents.add(event);
            } catch (Exception e) {
                failedCount++;
                log.error("[Outbox 발행 실패] eventId={}, topic={}, error={}",
                    event.getEventId(), event.getTopic(), e.getMessage());
            }
        }

        if (!publishedEvents.isEmpty()) {
            txTemplate.executeWithoutResult(status ->
                outboxJpaRepository.saveAll(publishedEvents));
        }
        if (!publishedEvents.isEmpty() || failedCount > 0) {
            log.info("[Outbox] {}건 발행 완료, {}건 실패 (다음 폴링에서 재시도)", publishedEvents.size(), failedCount);
        }
    }
}
