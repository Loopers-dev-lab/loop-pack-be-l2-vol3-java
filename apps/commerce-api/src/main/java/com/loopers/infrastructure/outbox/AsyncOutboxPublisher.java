package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncOutboxPublisher {
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final OutboxJpaRepository outboxJpaRepository;
    private final TransactionTemplate transactionTemplate;

    @Async("asyncExecutor")
    public void publishAsync(Long outboxId, String topic, String partitionKey, String payload) {
        try {
            kafkaTemplate.send(topic, partitionKey, payload).get(5, TimeUnit.SECONDS);
            transactionTemplate.executeWithoutResult(status ->
                    outboxJpaRepository.findById(outboxId).ifPresent(Outbox::markPublished)
            );
            log.info("Outbox 즉시 발행 성공: id={}, topic={}", outboxId, topic);
        } catch (Exception e) {
            log.warn("Outbox 즉시 발행 실패, 스케줄러가 재시도: outboxId={}", outboxId, e);
        }
    }
}
