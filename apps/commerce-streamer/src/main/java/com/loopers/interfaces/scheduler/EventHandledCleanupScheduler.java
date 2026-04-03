package com.loopers.interfaces.scheduler;

import com.loopers.domain.idempotent.EventHandledRepository;
import com.loopers.domain.log.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class EventHandledCleanupScheduler {

    /**
     * event_handled 보존 기간.
     * 이 기간이 지나면 멱등성 체크(existsByEventId)를 통과하므로,
     * Outbox FAILED 레코드의 수동 재발행은 반드시 이 기간 이내에 처리해야 한다.
     */
    private static final int RETENTION_DAYS = 7;

    private final EventHandledRepository eventHandledRepository;
    private final EventLogRepository eventLogRepository;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanup() {
        ZonedDateTime before = ZonedDateTime.now().minusDays(RETENTION_DAYS);
        eventHandledRepository.deleteHandledBefore(before);
        eventLogRepository.deleteLogsBefore(before);
        log.info("event_handled + event_log cleanup 완료: {}일 이전 레코드 삭제", RETENTION_DAYS);
    }
}
