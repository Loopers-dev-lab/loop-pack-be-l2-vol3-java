package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.infrastructure.kafka.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayScheduler {

    private static final int RELAY_LIMIT = 100;

    private final OutboxRepository outboxRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:30000}", initialDelayString = "${outbox.relay.initial-delay-ms:0}")
    @SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
    public void compensate() {
        List<OutboxModel> pending = outboxRepository.findPendingWithLimit(RELAY_LIMIT);
        if (pending.isEmpty()) {
            return;
        }

        List<CompletableFuture<SendResult<Object, Object>>> futures = pending.stream()
                .map(kafkaEventPublisher::send)
                .toList();

        for (int i = 0; i < pending.size(); i++) {
            OutboxModel outbox = pending.get(i);
            try {
                futures.get(i).get(30, TimeUnit.SECONDS);
                outbox.markPublished();
            } catch (Exception e) {
                outbox.markFailed();
                log.error("[OUTBOX_RELAY_FAILED] id={}, retryCount={}", outbox.getId(), outbox.getRetryCount(), e);
            }
            outboxRepository.save(outbox);
        }
    }
}
