package com.loopers.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventHandledCleanupScheduler {
    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanup() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(30);
        int deleted = eventHandledJpaRepository.deleteHandledBefore(cutoff);
        log.info("EventHandled 정리 완료: {}건 삭제 (30일 이상 경과)", deleted);
    }
}
