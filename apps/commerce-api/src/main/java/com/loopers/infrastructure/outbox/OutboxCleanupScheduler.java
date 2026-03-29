package com.loopers.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupScheduler {
    private final OutboxJpaRepository outboxJpaRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanup() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(7);
        int deleted = outboxJpaRepository.deletePublishedBefore(cutoff);
        log.info("Outbox 정리 완료: {}건 삭제 (7일 이상 경과)", deleted);
    }
}
