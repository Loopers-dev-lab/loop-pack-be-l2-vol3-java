package com.loopers.application.queue;

import com.loopers.domain.queue.EntrySchedulerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기열 입장 배치를 주기적으로 돌리는 애플리케이션 컴포넌트.
 * <p>
 * 실제 pop·분산 락·토큰 발급 규칙은 {@link EntrySchedulerService}에 두고,
 * 이 클래스는 {@link EntrySchedulerProperties} 값을 넘긴다.
 * 틱 간격은 {@code @Scheduled}의 {@code queue.scheduler.tick-ms}와 동일 키를 쓴다.
 */
@Component
public class EntryScheduler {

    private final EntrySchedulerService entrySchedulerService;
    private final EntrySchedulerProperties schedulerProperties;

    public EntryScheduler(
            EntrySchedulerService entrySchedulerService,
            EntrySchedulerProperties schedulerProperties
    ) {
        this.entrySchedulerService = entrySchedulerService;
        this.schedulerProperties = schedulerProperties;
    }

    /**
     * 한 틱마다 {@link EntrySchedulerService#releaseEntries}를 호출한다.
     * fixed delay(ms)는 {@code queue.scheduler.tick-ms}(기본 100)이다.
     */
    @Scheduled(fixedDelayString = "${queue.scheduler.tick-ms:100}")
    public void releaseEntries() {
        EntrySchedulerProperties p = schedulerProperties;
        entrySchedulerService.releaseEntries(
                p.eventId(),
                p.maxBatchSize(),
                p.tokenTtlSeconds(),
                p.lockTtlSeconds(),
                p.lockKey(),
                p.heartbeatKey(),
                p.heartbeatTtlSeconds()
        );
    }
}
