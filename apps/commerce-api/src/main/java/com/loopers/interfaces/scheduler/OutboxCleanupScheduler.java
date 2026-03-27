package com.loopers.interfaces.scheduler;

import com.loopers.support.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@Component
@ConditionalOnProperty(name = "scheduler.outbox.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    private static final int RETENTION_DAYS = 7;

    private final OutboxEventRepository outboxEventRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanup() {
        ZonedDateTime before = ZonedDateTime.now().minusDays(RETENTION_DAYS);
        outboxEventRepository.deleteSentBefore(before);
        log.info("Outbox cleanup 완료: {}일 이전 SENT 레코드 삭제", RETENTION_DAYS);
    }
}
