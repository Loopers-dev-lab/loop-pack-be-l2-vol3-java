package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.infrastructure.kafka.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayScheduler {

    @Value("${outbox.relay.limit:100}")
    private int relayLimit;

    private final OutboxRepository outboxRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:1000}", initialDelayString = "${outbox.relay.initial-delay-ms:0}")
    @SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT180S", lockAtLeastFor = "PT0S")
    public void compensate() {
        List<OutboxModel> pending = outboxRepository.findPendingWithLimit(relayLimit);
        if (pending.isEmpty()) {
            return;
        }

        List<CompletableFuture<SendResult<Object, Object>>> futures = pending.stream()
                .map(kafkaEventPublisher::send)
                .toList();

        List<Long> publishedIds = new ArrayList<>();
        List<OutboxModel> failedOutboxes = new ArrayList<>();

        for (int i = 0; i < pending.size(); i++) {
            OutboxModel outbox = pending.get(i);
            try {
                futures.get(i).get(30, TimeUnit.SECONDS);
                outbox.markPublished();
                publishedIds.add(outbox.getId());
            } catch (Exception e) {
                outbox.markFailed();
                failedOutboxes.add(outbox);
                log.error("[OUTBOX_RELAY_FAILED] id={}, retryCount={}", outbox.getId(), outbox.getRetryCount(), e);
            }
        }

        outboxRepository.markAllPublished(publishedIds);
        failedOutboxes.forEach(outboxRepository::save);
    }
}
