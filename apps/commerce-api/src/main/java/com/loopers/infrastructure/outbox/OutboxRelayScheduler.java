package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {
    private final OutboxJpaRepository outboxJpaRepository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<Outbox> failed = transactionTemplate.execute(status ->
                outboxJpaRepository.findUnpublishedBefore(
                        ZonedDateTime.now().minusSeconds(5), PageRequest.of(0, 100))
        );
        if (failed == null || failed.isEmpty()) return;

        for (Outbox outbox : failed) {
            try {
                kafkaTemplate.send(outbox.getTopic(), outbox.getPartitionKey(), outbox.getPayload())
                        .get(5, TimeUnit.SECONDS);
                transactionTemplate.executeWithoutResult(status ->
                        outboxJpaRepository.findById(outbox.getId())
                                .ifPresent(Outbox::markPublished)
                );
            } catch (Exception e) {
                log.error("Outbox fallback relay 실패: outboxId={}", outbox.getId(), e);
            }
        }
        log.info("Outbox fallback relay 완료: {}건 재시도 처리", failed.size());
    }
}
